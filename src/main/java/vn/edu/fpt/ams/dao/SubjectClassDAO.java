package vn.edu.fpt.ams.dao;

import java.util.*;
import java.sql.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.Jdbc;

/** JDBC boundary for class identity, capacity and Teacher assignment. */
public class SubjectClassDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Subject Class JDBC access");
    }

    public Optional<SubjectClass> findById(long classId) { throw pending(); }
    public Optional<SubjectClass> findByCode(String classCode) { throw pending(); }
    public List<SubjectClass> findBySubjectTerm(long subjectTermId) { throw pending(); }
    public List<SubjectClass> findManagedByExpert(long expertId, Long subjectId, Long termId) { throw pending(); }
    public List<SubjectClass> findAssignedToTeacher(long teacherId) { throw pending(); }
    public long insert(SubjectClass subjectClass) { throw pending(); }
    public void update(SubjectClass subjectClass) { throw pending(); }
    public void updateStatus(long classId, ClassStatus status) { throw pending(); }
    /** Run under an authorization and transaction boundary; Teacher must be active and eligible. */
    public void updateTeacher(long classId, Long teacherId) { throw pending(); }
    public List<ClassExpert> findExpertsByClassId(long classId, Boolean active) { throw pending(); }
    public long assignExpert(long classId, long subjectId, long expertId) { throw pending(); }
    public void deactivateExpert(long classExpertId) { throw pending(); }

    public List<AssignedSubjectDTO> findAssignedSubjects(Connection c, long expertId)
            throws SQLException {
        String sql = "SELECT s.subject_id,s.subject_code,s.subject_name,s.status,se.responsibility_type,"
                + "COUNT(DISTINCT CASE WHEN se.responsibility_type='PRIMARY' OR ce.class_expert_id IS NOT NULL"
                + " THEN sc.class_id END) accessible_classes FROM subject_experts se"
                + " JOIN subjects s ON s.subject_id=se.subject_id"
                + " LEFT JOIN subject_terms st ON st.subject_id=s.subject_id"
                + " LEFT JOIN subject_classes sc ON sc.subject_term_id=st.subject_term_id"
                + " LEFT JOIN class_experts ce ON ce.class_id=sc.class_id AND ce.subject_id=s.subject_id"
                + " AND ce.expert_id=se.expert_id AND ce.is_active=TRUE"
                + " WHERE se.expert_id=? AND se.is_active=TRUE"
                + " GROUP BY s.subject_id,s.subject_code,s.subject_name,s.status,se.responsibility_type"
                + " ORDER BY s.subject_code";
        try (var p=Jdbc.statement(c, sql, expertId); var r=p.executeQuery()) {
            var out=new ArrayList<AssignedSubjectDTO>();
            while(r.next()) out.add(new AssignedSubjectDTO(r.getLong(1),r.getString(2),r.getString(3),
                    SubjectStatus.valueOf(r.getString(4)),ResponsibilityType.valueOf(r.getString(5)),r.getLong(6)));
            return out;
        }
    }

    public List<AssignedClassDTO> findManagedByExpert(
            Connection c, long expertId, Long subjectId, Long termId, String keyword)
            throws SQLException {
        String sql = "SELECT sc.class_id,s.subject_id,s.subject_code,s.subject_name,se.responsibility_type,"
                + "at.term_id,at.term_code,at.term_name,sc.class_code,sc.class_name,sc.capacity,"
                + "(SELECT COUNT(*) FROM class_enrollments e WHERE e.class_id=sc.class_id AND e.status='ACTIVE'),"
                + "u.full_name,u.email,sc.registration_enabled,sc.status"
                + " FROM subject_experts se JOIN subjects s ON s.subject_id=se.subject_id"
                + " JOIN subject_terms st ON st.subject_id=s.subject_id"
                + " JOIN academic_terms at ON at.term_id=st.term_id"
                + " JOIN subject_classes sc ON sc.subject_term_id=st.subject_term_id"
                + " LEFT JOIN users u ON u.user_id=sc.teacher_id"
                + " WHERE se.expert_id=? AND se.is_active=TRUE"
                + " AND (se.responsibility_type='PRIMARY' OR EXISTS (SELECT 1 FROM class_experts ce"
                + " WHERE ce.class_id=sc.class_id AND ce.subject_id=s.subject_id"
                + " AND ce.expert_id=se.expert_id AND ce.is_active=TRUE))";
        var args=new ArrayList<Object>(); args.add(expertId);
        if(subjectId!=null){ sql+=" AND s.subject_id=?"; args.add(subjectId); }
        if(termId!=null){ sql+=" AND at.term_id=?"; args.add(termId); }
        if(keyword!=null&&!keyword.isBlank()){
            sql+=" AND (lower(s.subject_code) LIKE ? OR lower(s.subject_name) LIKE ?"
                    + " OR lower(sc.class_code) LIKE ? OR lower(sc.class_name) LIKE ?)";
            String pattern="%"+keyword.strip().toLowerCase(Locale.ROOT)+"%";
            args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
        }
        sql+=" ORDER BY at.starts_at DESC,s.subject_code,sc.class_code";
        try(var p=Jdbc.statement(c,sql,args.toArray()); var r=p.executeQuery()){
            var out=new ArrayList<AssignedClassDTO>();
            while(r.next()) out.add(new AssignedClassDTO(r.getLong(1),r.getLong(2),r.getString(3),
                    r.getString(4),ResponsibilityType.valueOf(r.getString(5)),r.getLong(6),r.getString(7),
                    r.getString(8),r.getString(9),r.getString(10),r.getInt(11),r.getLong(12),
                    r.getString(13),r.getString(14),r.getBoolean(15),ClassStatus.valueOf(r.getString(16))));
            return out;
        }
    }

    public List<TermOptionDTO> findAccessibleTerms(Connection c, long expertId) throws SQLException {
        String sql="SELECT DISTINCT at.term_id,at.term_code,at.term_name,at.starts_at"
                + " FROM subject_experts se JOIN subject_terms st ON st.subject_id=se.subject_id"
                + " JOIN academic_terms at ON at.term_id=st.term_id"
                + " JOIN subject_classes sc ON sc.subject_term_id=st.subject_term_id"
                + " WHERE se.expert_id=? AND se.is_active=TRUE AND"
                + " (se.responsibility_type='PRIMARY' OR EXISTS (SELECT 1 FROM class_experts ce"
                + " WHERE ce.class_id=sc.class_id AND ce.subject_id=st.subject_id"
                + " AND ce.expert_id=se.expert_id AND ce.is_active=TRUE))"
                + " ORDER BY at.starts_at DESC";
        try(var p=Jdbc.statement(c,sql,expertId); var r=p.executeQuery()){
            var out=new ArrayList<TermOptionDTO>();
            while(r.next()) out.add(new TermOptionDTO(r.getLong(1),r.getString(2),r.getString(3)));
            return out;
        }
    }

    static SubjectClass mapSubjectClass(ResultSet rs) throws SQLException {
        return new SubjectClass(rs.getLong("class_id"), rs.getLong("subject_term_id"),
                ResultSetSupport.nullableLong(rs, "teacher_id"), rs.getString("class_code"),
                rs.getString("class_name"), rs.getInt("capacity"),
                rs.getBoolean("registration_enabled"),
                ClassStatus.valueOf(rs.getString("status")));
    }

    static ClassExpert mapClassExpert(ResultSet rs) throws SQLException {
        return new ClassExpert(rs.getLong("class_expert_id"), rs.getLong("class_id"),
                rs.getLong("subject_id"), rs.getLong("expert_id"), rs.getBoolean("is_active"),
                ResultSetSupport.instant(rs, "assigned_at"),
                ResultSetSupport.instant(rs, "deactivated_at"));
    }
}
