package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.DBConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.Map;
import vn.edu.fpt.ams.util.AssignmentDraftValidator;
import vn.edu.fpt.ams.util.AssignmentEditSupport;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.AssignmentPublicationValidator;
import vn.edu.fpt.ams.util.AssignmentSubmissionAccess;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class AssignmentService {
    private final AssignmentDAO dao;
    private final ConnectionProvider connections;

    @FunctionalInterface
    public interface ConnectionProvider { Connection open() throws SQLException; }

    public AssignmentService() { this(new AssignmentDAO(), DBConnection::getConnection); }

    public AssignmentService(AssignmentDAO dao, ConnectionProvider connections) {
        this.dao = Objects.requireNonNull(dao);
        this.connections = Objects.requireNonNull(connections);
    }

    public AssignmentCreatePage createPage(long teacherId, long classId) {
        if (classId <= 0) throw new IllegalArgumentException("Lớp không hợp lệ.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            if (!dao.isActiveTeacher(connection, teacherId))
                throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
            return dao.findCreatePage(connection, teacherId, classId, false);
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể tải biểu mẫu tạo bài tập.", ex);
        }
    }

    public long createAssignment(long teacherId, AssignmentForm form) {
        var draft = AssignmentDraftValidator.validate(form);
        Assignment assignment = draft.assignment();
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                // Keep authorization valid until both parent and nested content are saved.
                if (!dao.lockActiveTeacher(connection, teacherId))
                    throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                var context = dao.findCreatePage(connection, teacherId, assignment.classId(), true);
                if (!dao.lockActiveTemplate(connection, assignment.templateId(), context.subjectId()))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("templateId",
                            "Hãy chọn mẫu đang hoạt động thuộc môn học của lớp này."));
                long id = dao.insertDraft(connection, assignment);
                dao.insertDraftQuestions(connection, id, draft.questions());
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể lưu bản nháp.", ex);
        }
    }

    public AssignmentEditPage editPage(long teacherId, long assignmentId) {
        if(teacherId<=0) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
        if(assignmentId<=0) throw new IllegalArgumentException("Bài tập không hợp lệ.");
        try(Connection connection=connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if(!dao.isActiveTeacher(connection,teacherId)) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                var detail=dao.findDetailForTeacher(connection,teacherId,assignmentId)
                        .orElseThrow(()->new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền truy cập."));
                requireDraft(detail.assignment());
                var questions=dao.findAllDraftQuestions(connection,assignmentId,false);
                var context=dao.findCreatePage(connection,teacherId,detail.assignment().classId(),false);
                if(context.templates().stream().noneMatch(t->t.templateId()==detail.assignment().templateId())) {
                    var templates=new java.util.ArrayList<>(context.templates());
                    templates.add(0,new AssignmentCreatePage.TemplateOption(detail.assignment().templateId(),
                            detail.assignment().title(),detail.assignment().instructions(),detail.assignment().maximumScore(),
                            detail.templateTitle()+" (mẫu hiện tại, đã ngừng hoạt động)"));
                    context=new AssignmentCreatePage(context.classId(),context.subjectId(),context.classCode(),context.className(),
                            context.subjectCode(),context.subjectName(),List.copyOf(templates));
                }
                connection.commit();
                return new AssignmentEditPage(context,AssignmentEditSupport.form(detail.assignment(),questions));
            } catch(SQLException|RuntimeException ex) {
                try{connection.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("Không thể tải bản nháp để chỉnh sửa.",ex); }
    }

    public void updateAssignment(long teacherId, long assignmentId, AssignmentForm form) {
        if(teacherId<=0) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
        if(assignmentId<=0) throw new IllegalArgumentException("Bài tập không hợp lệ.");
        try(Connection connection=connections.open()) {
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            try {
                if(!dao.lockActiveTeacher(connection,teacherId)) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                Assignment stored=dao.lockOwnedAssignment(connection,teacherId,assignmentId);
                var context=dao.findCreatePage(connection,teacherId,stored.classId(),true);
                requireDraft(stored);
                var existing=dao.findAllDraftQuestions(connection,assignmentId,true);
                if(!form.value("revision").matches("[0-9a-f]{64}"))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("revision","Hãy mở lại trang chỉnh sửa trước khi lưu bản nháp."));
                if(!AssignmentEditSupport.revision(stored,existing).equals(form.value("revision")))
                    throw new AssignmentEditConflictException("Bản nháp đã thay đổi sau khi bạn mở trang chỉnh sửa. Hãy tải phiên bản mới nhất trước khi lưu.");
                var draft=AssignmentEditSupport.validate(form,existing);
                if(draft.assignment().classId()!=stored.classId())
                    throw new AssignmentDraftValidator.ValidationException(Map.of("classId","Không thể chuyển bài tập sang lớp khác."));
                if(draft.assignment().templateId()!=stored.templateId()
                        && !dao.lockActiveTemplate(connection,draft.assignment().templateId(),context.subjectId()))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("templateId","Hãy chọn mẫu đang hoạt động thuộc môn học của lớp này."));
                dao.updateDraft(connection,assignmentId,draft.assignment());
                dao.replaceDraftQuestions(connection,assignmentId,existing,draft.questions());
                connection.commit();
            } catch(SQLException|RuntimeException ex) {
                try{connection.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("Không thể lưu thay đổi của bản nháp.",ex); }
    }

    private static void requireDraft(Assignment a) {
        if(a.status()!=AssignmentStatus.DRAFT)
            throw new AssignmentEditConflictException("Chỉ có thể sửa bài tập ở trạng thái bản nháp. Trạng thái hiện tại: "+vn.edu.fpt.ams.util.AssignmentListView.label(a.status())+".");
    }

    public long previewTeacherId() {
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            return dao.findPreviewTeacherId(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể tải giảng viên dùng để xem thử.", ex);
        }
    }

    public AssignmentListPage listPage(long teacherId, Long classId, AssignmentListFilter filter) {
        if (teacherId <= 0) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
        if (classId == null || classId <= 0) throw new IllegalArgumentException("Hãy chọn một lớp từ bảng điều khiển.");
        Objects.requireNonNull(filter, "Cần cung cấp bộ lọc bài tập.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if (!dao.isActiveTeacher(connection, teacherId))
                    throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                var classes = dao.findClassesForTeacher(connection, teacherId);
                if (classes.stream().noneMatch(c -> c.classId() == classId))
                    throw new SecurityException("Bạn không có quyền xem bài tập của lớp này.");
                Long selected = classId;
                List<Assignment> assignments = dao.findForTeacher(connection, teacherId, selected, filter);
                java.util.Set<Long> submissionAssignmentIds = new java.util.HashSet<>();
                for (Assignment assignment : assignments) {
                    boolean hasSubmittedWork = assignment.status()!=AssignmentStatus.PUBLISHED
                            && assignment.publishedAt()!=null && dao.hasSubmittedWork(connection,assignment.assignmentId());
                    if (AssignmentSubmissionAccess.canView(assignment,hasSubmittedWork))
                        submissionAssignmentIds.add(assignment.assignmentId());
                }
                connection.commit();
                return new AssignmentListPage(classes, selected, filter, assignments, submissionAssignmentIds);
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể tải danh sách bài tập.", ex);
        }
    }

    public java.util.List<Assignment> listAssignments(long teacherId, long classId) {
        return listPage(teacherId, classId, AssignmentListFilter.from(null, null, null, null)).assignments();
    }
    public AssignmentDetailPage detailPage(long teacherId, long assignmentId) {
        if (teacherId <= 0) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
        if (assignmentId <= 0) throw new IllegalArgumentException("Mã bài tập phải là số nguyên dương.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if (!dao.isActiveTeacher(connection, teacherId))
                    throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                var page = dao.findDetailForTeacher(connection, teacherId, assignmentId)
                        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền truy cập."));
                var questions = page.assignment().responseType() == ResponseType.OBJECTIVE
                        ? dao.findQuestionsForTeacher(connection, teacherId, assignmentId) : List.<Question>of();
                boolean canViewSubmissions=AssignmentSubmissionAccess.canView(page.assignment(),
                        page.assignment().status()!=AssignmentStatus.PUBLISHED && page.assignment().publishedAt()!=null
                                && dao.hasSubmittedWork(connection,assignmentId));
                connection.commit();
                return new AssignmentDetailPage(page.assignment(), page.classCode(), page.className(),
                        page.subjectCode(), page.subjectName(), page.termCode(), page.templateTitle(), questions,canViewSubmissions);
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể tải chi tiết bài tập.", ex);
        }
    }

    public Assignment findAssignment(long teacherId, long assignmentId) {
        return detailPage(teacherId, assignmentId).assignment();
    }
    public AssignmentLifecyclePage lifecyclePage(long teacherId,long assignmentId,String action) {
        checkLifecycleInputs(teacherId,assignmentId,action);
        try(Connection connection=connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if(!dao.isActiveTeacher(connection,teacherId)) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                var detail=dao.findDetailForTeacher(connection,teacherId,assignmentId)
                        .orElseThrow(()->new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền truy cập."));
                var questions=dao.findAllDraftQuestions(connection,assignmentId,false);
                var a=detail.assignment();
                Map<String,String> errors=Map.of();
                if("publish".equals(action) && a.status()==AssignmentStatus.DRAFT)
                    errors=AssignmentPublicationValidator.errors(a,questions,dao.currentTime(connection));
                int count="cancel".equals(action)?dao.countInProgressSubmissions(connection,assignmentId):0;
                var snapshot=new AssignmentDetailPage(a,detail.classCode(),detail.className(),detail.subjectCode(),
                        detail.subjectName(),detail.termCode(),detail.templateTitle(),questions);
                connection.commit();
                return new AssignmentLifecyclePage(snapshot,action,AssignmentEditSupport.revision(a,questions),errors,count);
            } catch(SQLException|RuntimeException ex) {
                try { connection.rollback(); } catch(SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("Không thể tải trang xác nhận bài tập.",ex); }
    }

    public void publishAssignment(long teacherId,long assignmentId,String revision) {
        changeLifecycle(teacherId,assignmentId,revision,"publish");
    }

    public int cancelAssignment(long teacherId,long assignmentId,String revision) {
        return changeLifecycle(teacherId,assignmentId,revision,"cancel");
    }

    public void restoreAssignment(long teacherId,long assignmentId,String revision) {
        throw new IllegalArgumentException("Bài tập đã hủy không thể chuyển về bản nháp.");
    }

    private int changeLifecycle(long teacherId,long assignmentId,String revision,String action) {
        checkLifecycleInputs(teacherId,assignmentId,action);
        try(Connection connection=connections.open()) {
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            try {
                if(!dao.lockActiveTeacher(connection,teacherId)) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
                var a=dao.lockOwnedAssignment(connection,teacherId,assignmentId);
                dao.findCreatePage(connection,teacherId,a.classId(),true);
                boolean publishing="publish".equals(action);
                boolean cancelling="cancel".equals(action);
                if(revision==null || !revision.matches("[0-9a-f]{64}"))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("revision","Hãy mở lại trang xác nhận trước khi tiếp tục."));
                if((publishing && a.status()==AssignmentStatus.PUBLISHED)
                        || (cancelling && a.status()==AssignmentStatus.CANCELLED)) {
                    // Repeated confirmations keep the original timestamps and outcome.
                    connection.commit(); return 0;
                }
                if(publishing && a.status()!=AssignmentStatus.DRAFT)
                    throw new AssignmentEditConflictException("Bài tập đã hủy không thể công bố lại.");
                var questions=dao.findAllDraftQuestions(connection,assignmentId,true);
                if(!AssignmentEditSupport.revision(a,questions).equals(revision))
                    throw new AssignmentEditConflictException("Bài tập đã thay đổi sau khi bạn mở trang xác nhận. Hãy kiểm tra nội dung mới nhất trước khi xác nhận lại.");
                var now=dao.currentTime(connection);
                int voided=0;
                if(publishing) {
                    var errors=AssignmentPublicationValidator.errors(a,questions,now);
                    if(!errors.isEmpty()) throw new AssignmentDraftValidator.ValidationException(errors);
                    dao.markPublished(connection,assignmentId,now);
                } else if(cancelling) {
                    dao.markCancelled(connection,assignmentId,now);
                    voided=dao.voidInProgressSubmissions(connection,assignmentId);
                }
                connection.commit(); return voided;
            } catch(SQLException|RuntimeException ex) {
                try { connection.rollback(); } catch(SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("Không thể hoàn tất thao tác. Hãy kiểm tra trạng thái hiện tại của bài tập trước khi thử lại.",ex); }
    }

    private static void checkLifecycleInputs(long teacherId,long assignmentId,String action) {
        if(teacherId<=0) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
        if(assignmentId<=0) throw new IllegalArgumentException("Bài tập không hợp lệ.");
        if(!"publish".equals(action) && !"cancel".equals(action))
            throw new IllegalArgumentException("Thao tác bài tập không hợp lệ.");
    }
}
