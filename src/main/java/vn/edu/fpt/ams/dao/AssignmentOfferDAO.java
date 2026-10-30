package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import vn.edu.fpt.ams.dto.AssignmentOfferView;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.util.Jdbc;

/** SQL for teacher offers; callers own authorization, connections and transactions. */
public class AssignmentOfferDAO {
    public record Offer(long requestId, long offeredBy, AssignmentOfferStatus status, Instant sentAt, Instant expiresAt) {}
    public record Request(long sourceClassId, Long createdClassId, String status) {}
    public record ClassData(long id, long subjectTermId, Long teacherId, String code, int capacity, boolean registrationEnabled) {}
    public record Offering(long subjectId, String subjectCode, boolean active) {}

    /** Read the server wall clock after locks, rather than PostgreSQL's transaction start time. */
    public Instant now(Connection c) throws SQLException {
        String clock = c.getMetaData().getDatabaseProductName().equals("PostgreSQL") ? "clock_timestamp()" : "CURRENT_TIMESTAMP";
        try (var p = Jdbc.statement(c, "SELECT " + clock); var r = p.executeQuery()) {
            r.next(); return r.getTimestamp(1).toInstant();
        }
    }

    /** Expire only pending episodes; requests remain approved for renewed Primary handling. */
    public int expire(Connection c, Long teacherId, Instant now) throws SQLException {
        String sql = "UPDATE additional_class_assignment_offers SET status='EXPIRED' WHERE status='PENDING' AND expires_at<=?";
        return teacherId == null ? Jdbc.update(c, sql, now)
                : Jdbc.update(c, sql + " AND teacher_id=?", now, teacherId);
    }

    /** List offers addressed to this teacher without granting access to another teacher's records. */
    public List<AssignmentOfferView> list(Connection c, long teacherId) throws SQLException {
        String sql = """
                SELECT o.*,r.overflow_context,src.class_code source_code,s.subject_code,s.subject_name,
                       t.term_code,u.full_name sender_name,r.created_class_id,dst.class_code created_code,
                       COALESCE(dst.capacity,src.capacity) proposed_capacity
                FROM additional_class_assignment_offers o
                JOIN additional_class_requests r ON r.request_id=o.request_id
                JOIN overflow_batches b ON b.overflow_batch_id=r.overflow_batch_id
                JOIN subject_classes src ON src.class_id=b.source_class_id
                JOIN subject_terms st ON st.subject_term_id=src.subject_term_id
                JOIN subjects s ON s.subject_id=st.subject_id
                JOIN academic_terms t ON t.term_id=st.term_id
                JOIN users u ON u.user_id=o.offered_by
                LEFT JOIN subject_classes dst ON dst.class_id=r.created_class_id
                WHERE o.teacher_id=? ORDER BY o.sent_at DESC,o.offer_id DESC
                """;
        try (var p=Jdbc.statement(c,sql,teacherId); var r=p.executeQuery()) {
            var out=new ArrayList<AssignmentOfferView>();
            while(r.next()) out.add(new AssignmentOfferView(r.getLong("offer_id"),r.getLong("request_id"),
                    r.getString("source_code"),r.getString("subject_code"),r.getString("subject_name"),
                    r.getString("term_code"),r.getString("sender_name"),r.getString("overflow_context"),
                    AssignmentOfferStatus.valueOf(r.getString("status")),ResultSetSupport.instant(r,"sent_at"),
                    ResultSetSupport.instant(r,"expires_at"),ResultSetSupport.instant(r,"responded_at"),
                    r.getString("response_note"),ResultSetSupport.nullableLong(r,"created_class_id"),
                    r.getString("created_code"),r.getInt("proposed_capacity")));
            return out;
        }
    }

    /** Resolve the request only for an offer owned by the authenticated recipient. */
    public long requestId(Connection c,long teacher,long offer) throws SQLException {
        long id=Jdbc.scalar(c,"SELECT request_id FROM additional_class_assignment_offers WHERE offer_id=? AND teacher_id=?",offer,teacher);
        if(id==0) throw new NoSuchElementException("Không tìm thấy lời mời gửi cho bạn.");
        return id;
    }

    /** Serialize all offer decisions and class finalization for one request. */
    public Request lockRequest(Connection c,long id) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT * FROM additional_class_requests WHERE request_id=? FOR UPDATE",id);var r=p.executeQuery()) {
            if(!r.next()) throw new NoSuchElementException("Không tìm thấy yêu cầu lớp bổ sung.");
            long batch=r.getLong("overflow_batch_id");
            return new Request(Jdbc.scalar(c,"SELECT source_class_id FROM overflow_batches WHERE overflow_batch_id=?",batch),
                    ResultSetSupport.nullableLong(r,"created_class_id"),r.getString("status"));
        }
    }

    /** Lock and reload the current episode after its request is locked. */
    public Offer lockOffer(Connection c,long teacher,long id) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT * FROM additional_class_assignment_offers WHERE offer_id=? AND teacher_id=? FOR UPDATE",id,teacher);var r=p.executeQuery()) {
            if(!r.next()) throw new NoSuchElementException("Không tìm thấy lời mời gửi cho bạn.");
            return new Offer(r.getLong("request_id"),r.getLong("offered_by"),
                    AssignmentOfferStatus.valueOf(r.getString("status")),ResultSetSupport.instant(r,"sent_at"),ResultSetSupport.instant(r,"expires_at"));
        }
    }

    /** Lock class setup so finalization cannot race another assignment or capacity edit. */
    public ClassData lockClass(Connection c,long id) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT * FROM subject_classes WHERE class_id=? FOR UPDATE",id);var r=p.executeQuery()) {
            if(!r.next()) throw new IllegalArgumentException("Không tìm thấy lớp cấu hình cho yêu cầu.");
            return new ClassData(id,r.getLong("subject_term_id"),ResultSetSupport.nullableLong(r,"teacher_id"),
                    r.getString("class_code"),r.getInt("capacity"),r.getBoolean("registration_enabled"));
        }
    }

    /** Check the offering's subject and term remain active while finishing the class. */
    public Offering offering(Connection c,long subjectTermId) throws SQLException {
        String sql="SELECT s.subject_id,s.subject_code,s.status,t.is_active FROM subject_terms st "
                +"JOIN subjects s ON s.subject_id=st.subject_id JOIN academic_terms t ON t.term_id=st.term_id "
                +"WHERE st.subject_term_id=? FOR UPDATE";
        try(var p=Jdbc.statement(c,sql,subjectTermId);var r=p.executeQuery()) {
            if(!r.next()) throw new IllegalArgumentException("Không tìm thấy môn học trong học kỳ.");
            return new Offering(r.getLong(1),r.getString(2),"ACTIVE".equals(r.getString(3))&&r.getBoolean(4));
        }
    }

    /** Lock active eligibility so account/qualification changes cannot race acceptance. */
    public boolean eligibleTeacher(Connection c,long teacher,long subject) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT teacher_subject_id FROM teacher_subjects WHERE teacher_id=? AND subject_id=? AND is_active=TRUE FOR UPDATE",teacher,subject);var r=p.executeQuery()) {
            return r.next();
        }
    }

    /** Validate all active class assignments refer to active Experts of the same Subject. */
    public List<Long> experts(Connection c,long classId,long subject) throws SQLException {
        String sql="""
                SELECT ce.expert_id,ce.subject_id,se.is_active subject_active,u.role,u.account_status
                FROM class_experts ce
                JOIN subject_experts se ON se.subject_id=ce.subject_id AND se.expert_id=ce.expert_id
                JOIN users u ON u.user_id=ce.expert_id
                WHERE ce.class_id=? AND ce.is_active=TRUE ORDER BY ce.expert_id FOR UPDATE
                """;
        var ids=new ArrayList<Long>();
        try(var p=Jdbc.statement(c,sql,classId);var r=p.executeQuery()) {
            while(r.next()) {
                if(r.getLong("subject_id")!=subject||!r.getBoolean("subject_active")
                        ||!"EXPERT".equals(r.getString("role"))||!"ACTIVE".equals(r.getString("account_status")))
                    throw new IllegalArgumentException("Chuyên gia phụ trách lớp không còn hợp lệ.");
                ids.add(r.getLong("expert_id"));
            }
        }
        if(ids.size()<1||ids.size()>2||ids.size()!=Jdbc.scalar(c,"SELECT COUNT(*) FROM class_experts WHERE class_id=? AND is_active=TRUE",classId))
            throw new IllegalArgumentException("Lớp cần 1–2 chuyên gia đang hoạt động cùng môn. Vui lòng liên hệ chuyên gia chính.");
        return ids;
    }

    /** Create a deterministic additional class only after consent; keep the source class untouched. */
    public long createClass(Connection c,long request,ClassData source,String subjectCode,long teacher,
                            long subject,List<Long> experts) throws SQLException {
        long id=Jdbc.insert(c,"INSERT INTO subject_classes(subject_term_id,teacher_id,class_code,class_name,capacity,registration_enabled,status) VALUES(?,?,?,?,?,?,'ACTIVE')",
                source.subjectTermId(),teacher,"ADD-"+request,"Lớp bổ sung "+request+" — "+subjectCode,
                source.capacity(),source.registrationEnabled());
        for(long expert:experts) Jdbc.update(c,"INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(?,?,?)",id,subject,expert);
        return id;
    }

    /** Finalize an existing unstaffed setup class, without changing its identity or offering. */
    public void staffClass(Connection c,long id,long teacher) throws SQLException {
        if(Jdbc.update(c,"UPDATE subject_classes SET teacher_id=?,status='ACTIVE' WHERE class_id=? AND teacher_id IS NULL",teacher,id)!=1)
            throw new IllegalArgumentException("Lớp bổ sung đã có giảng viên phụ trách.");
    }

    /** Count committed seats while the caller holds the setup class lock. */
    public long occupiedSeats(Connection c,long id,Instant now) throws SQLException {
        return Jdbc.scalar(c,"SELECT COUNT(*) FROM class_enrollments WHERE class_id=? AND status='ACTIVE'",id)
                +Jdbc.scalar(c,"SELECT COUNT(*) FROM student_invitations WHERE class_id=? AND status='PENDING' AND expires_at>?",id,now);
    }

    /** Persist a response once with the checked server time, never rewriting a terminal episode. */
    public void respond(Connection c,long id,long teacher,AssignmentOfferStatus status,String note,Instant now) throws SQLException {
        if(Jdbc.update(c,"UPDATE additional_class_assignment_offers SET status=?,responded_at=?,response_note=? WHERE offer_id=? AND teacher_id=? AND status='PENDING' AND sent_at<=? AND expires_at>?",
                status.name(),now,note,id,teacher,now,now)!=1) throw new IllegalArgumentException("Lời mời không còn hiệu lực.");
    }

    /** Resolve only an approved request, preserving its original review and deadline evidence. */
    public void resolve(Connection c,long request,long classId,Instant now) throws SQLException {
        if(Jdbc.update(c,"UPDATE additional_class_requests SET status='RESOLVED',created_class_id=?,resolved_at=? WHERE request_id=? AND status='APPROVED'",
                classId,now,request)!=1) throw new IllegalArgumentException("Yêu cầu đã được xử lý.");
    }
}
