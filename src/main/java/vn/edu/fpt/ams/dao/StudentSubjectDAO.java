package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.dto.StudentSubjectDTO.Offering;
import vn.edu.fpt.ams.util.Jdbc;

/** UC46-49. All private registration/placement joins are restricted to the session Student. */
public final class StudentSubjectDAO {
    private static final String ELIGIBLE_CLASS = """
            c.status='ACTIVE' AND c.registration_enabled=TRUE
            AND EXISTS(SELECT 1 FROM users u JOIN teacher_subjects ts ON ts.teacher_id=u.user_id
                WHERE u.user_id=c.teacher_id AND u.role='TEACHER' AND u.account_status='ACTIVE'
                    AND ts.subject_id=st.subject_id AND ts.is_active=TRUE)
            """;
    private static final String OCCUPIED = """
            (SELECT COUNT(*) FROM class_enrollments ce WHERE ce.class_id=c.class_id AND ce.status='ACTIVE')
            + (SELECT COUNT(*) FROM student_invitations i WHERE i.class_id=c.class_id
                AND i.status='PENDING' AND i.expires_at>statement_timestamp())
            """;
    private static final String SELECT = """
            SELECT st.subject_term_id,s.subject_code,s.subject_name,s.description,s.status subject_status,
                t.term_code,t.term_name,(t.is_active=TRUE AND t.ends_at>statement_timestamp()) AS is_active,
                (t.is_active=TRUE AND t.starts_at<=statement_timestamp() AND t.ends_at>statement_timestamp()) AS is_current,st.registration_enabled,
                st.registration_opens_at,st.registration_closes_at,r.status registration_status,
                own.class_code,own.class_name,teacher.full_name,own.status class_status,
                (SELECT COALESCE(SUM(GREATEST(0,c.capacity-(
            """ + OCCUPIED + "))),0) FROM subject_classes c WHERE c.subject_term_id=st.subject_term_id AND "
            + ELIGIBLE_CLASS + ") available_seats,statement_timestamp() server_now " + """
            FROM subject_terms st JOIN subjects s ON s.subject_id=st.subject_id
            JOIN academic_terms t ON t.term_id=st.term_id
            LEFT JOIN subject_registrations r ON r.subject_term_id=st.subject_term_id AND r.student_id=?
            LEFT JOIN class_enrollments e ON e.registration_id=r.registration_id AND e.status='ACTIVE'
            LEFT JOIN subject_classes own ON own.class_id=e.class_id AND own.subject_term_id=st.subject_term_id
            LEFT JOIN users teacher ON teacher.user_id=own.teacher_id
            """;
    // Publish the schedule before opening time; register() still checks Offering.open().
    private static final String VISIBLE = """
            s.status='ACTIVE' AND t.is_active=TRUE AND t.ends_at>statement_timestamp() AND st.registration_enabled=TRUE
            AND st.registration_closes_at>statement_timestamp()
            """;

    public List<Offering> list(Connection c, long student, boolean enrolled) throws SQLException {
        try(var p=Jdbc.statement(c, SELECT + " WHERE " + (enrolled ? "r.registration_id IS NOT NULL" : VISIBLE)
                + " ORDER BY t.starts_at DESC,s.subject_code,st.subject_term_id",student);var r=p.executeQuery()) {
            var out=new ArrayList<Offering>(); while(r.next()) out.add(map(r)); return out;
        }
    }
    public Offering detail(Connection c,long student,long offering) throws SQLException {
        try(var p=Jdbc.statement(c,SELECT+" WHERE st.subject_term_id=? AND (r.registration_id IS NOT NULL OR ("+VISIBLE+"))",student,offering);
                var r=p.executeQuery()) {
            if(!r.next()) throw new NoSuchElementException("Không tìm thấy môn học trong phạm vi được xem.");
            return map(r);
        }
    }
    private Offering map(ResultSet r) throws SQLException {
        return new Offering(r.getLong("subject_term_id"),r.getString("subject_code"),r.getString("subject_name"),
                r.getString("description"),r.getString("subject_status"),r.getString("term_code"),r.getString("term_name"),
                r.getBoolean("is_active"),r.getBoolean("is_current"),r.getBoolean("registration_enabled"),
                r.getTimestamp("registration_opens_at").toInstant(),r.getTimestamp("registration_closes_at").toInstant(),
                r.getString("registration_status"),r.getString("class_code"),r.getString("class_name"),
                r.getString("full_name"),r.getString("class_status"),r.getLong("available_seats"),r.getTimestamp("server_now").toInstant());
    }

    public record Context(long subject,long term) {}
    public Context context(Connection c,long offering) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT subject_id,term_id FROM subject_terms WHERE subject_term_id=?",offering);var r=p.executeQuery()) {
            if(!r.next())throw new NoSuchElementException(); return new Context(r.getLong(1),r.getLong(2));
        }
    }
    public void lockOffering(Connection c,long offering,Context context) throws SQLException {
        // Stabilize lifecycle and offering configuration before allocating any seat.
        for(var entry:List.of(new Object[]{"SELECT term_id FROM academic_terms WHERE term_id=? FOR SHARE",context.term()},
                new Object[]{"SELECT subject_id FROM subjects WHERE subject_id=? FOR SHARE",context.subject()},
                new Object[]{"SELECT subject_term_id FROM subject_terms WHERE subject_term_id=? FOR UPDATE",offering})) {
            try(var p=Jdbc.statement(c,(String)entry[0],entry[1]);var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();}
        }
    }
    public List<Long> lockClasses(Connection c,long offering) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT class_id FROM subject_classes WHERE subject_term_id=? ORDER BY class_id FOR UPDATE",offering);
                var r=p.executeQuery()) {
            var out=new ArrayList<Long>();while(r.next())out.add(r.getLong(1));return out;
        }
    }
    public boolean seatAvailable(Connection c,long offering,long classId) throws SQLException {
        // Keep the assigned Teacher and Subject eligibility active through the placement commit.
        try(var p=Jdbc.statement(c,"SELECT u.user_id FROM subject_classes sc"
                +" JOIN subject_terms st ON st.subject_term_id=sc.subject_term_id"
                +" JOIN users u ON u.user_id=sc.teacher_id JOIN teacher_subjects ts ON ts.teacher_id=u.user_id AND ts.subject_id=st.subject_id"
                +" WHERE sc.class_id=? AND u.role='TEACHER' AND u.account_status='ACTIVE' AND ts.is_active=TRUE FOR SHARE OF u,ts",classId);
                var r=p.executeQuery()){if(!r.next())return false;}
        return Jdbc.scalar(c,"SELECT COUNT(*) FROM subject_classes c JOIN subject_terms st ON st.subject_term_id=c.subject_term_id"
                +" WHERE c.subject_term_id=? AND c.class_id=? AND "+ELIGIBLE_CLASS+" AND c.capacity>("+OCCUPIED+")",offering,classId)==1;
    }
    public void lockStudent(Connection c,long student) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT user_id FROM users WHERE user_id=? AND role='STUDENT' AND account_status='ACTIVE' FOR SHARE",student);
                var r=p.executeQuery()){if(!r.next())throw new SecurityException("Tài khoản sinh viên không còn hoạt động.");}
    }
    public boolean hasPendingInvitation(Connection c,long student,long offering) throws SQLException {
        return Jdbc.scalar(c,"SELECT COUNT(*) FROM student_invitations i JOIN subject_classes c ON c.class_id=i.class_id"
                +" JOIN users u ON lower(trim(u.email))=lower(trim(i.email)) WHERE u.user_id=? AND c.subject_term_id=?"
                +" AND i.status='PENDING' AND i.expires_at>statement_timestamp()",student,offering)>0;
    }
    public long savePlacement(Connection c,long student,long offering,long classId) throws SQLException {
        long registration=Jdbc.scalar(c,"SELECT registration_id FROM subject_registrations WHERE student_id=? AND subject_term_id=? FOR UPDATE",student,offering);
        if(registration!=0 && Jdbc.scalar(c,"SELECT COUNT(*) FROM class_enrollments WHERE registration_id=? AND status='ACTIVE'",registration)>0)
            throw new IllegalArgumentException("Bạn đã có lớp trong môn học này. Không thể đăng ký lần nữa.");
        if(registration==0) registration=Jdbc.insert(c,"INSERT INTO subject_registrations(student_id,subject_term_id) VALUES(?,?)",student,offering);
        else Jdbc.update(c,"UPDATE subject_registrations SET status='ACTIVE',deactivated_at=NULL WHERE registration_id=?",registration);
        Jdbc.insert(c,"INSERT INTO class_enrollments(registration_id,class_id,enrollment_method) VALUES(?,?,'SELF_REGISTRATION')",registration,classId);
        return registration;
    }
}
