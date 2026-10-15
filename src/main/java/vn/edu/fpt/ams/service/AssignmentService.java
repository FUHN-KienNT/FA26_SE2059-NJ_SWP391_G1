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
        if (classId <= 0) throw new IllegalArgumentException("Invalid class.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            if (!dao.isActiveTeacher(connection, teacherId))
                throw new SecurityException("An active Teacher account is required.");
            return dao.findCreatePage(connection, teacherId, classId, false);
        } catch (SQLException ex) {
            throw new IllegalStateException("The creation form could not be loaded.", ex);
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
                    throw new SecurityException("An active Teacher account is required.");
                var context = dao.findCreatePage(connection, teacherId, assignment.classId(), true);
                if (!dao.lockActiveTemplate(connection, assignment.templateId(), context.subjectId()))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("templateId",
                            "Select an active template belonging to this class's subject."));
                long id = dao.insertDraft(connection, assignment);
                dao.insertDraftQuestions(connection, id, draft.questions());
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("The draft could not be saved.", ex);
        }
    }

    public AssignmentEditPage editPage(long teacherId, long assignmentId) {
        if(teacherId<=0) throw new SecurityException("An active Teacher account is required.");
        if(assignmentId<=0) throw new IllegalArgumentException("Invalid assignment.");
        try(Connection connection=connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if(!dao.isActiveTeacher(connection,teacherId)) throw new SecurityException("An active Teacher account is required.");
                var detail=dao.findDetailForTeacher(connection,teacherId,assignmentId)
                        .orElseThrow(()->new NoSuchElementException("Assignment not found or unavailable."));
                requireDraft(detail.assignment());
                var questions=dao.findAllDraftQuestions(connection,assignmentId,false);
                var context=dao.findCreatePage(connection,teacherId,detail.assignment().classId(),false);
                if(context.templates().stream().noneMatch(t->t.templateId()==detail.assignment().templateId())) {
                    var templates=new java.util.ArrayList<>(context.templates());
                    templates.add(0,new AssignmentCreatePage.TemplateOption(detail.assignment().templateId(),
                            detail.assignment().title(),detail.assignment().instructions(),detail.assignment().maximumScore(),
                            detail.templateTitle()+" (current source, inactive)"));
                    context=new AssignmentCreatePage(context.classId(),context.subjectId(),context.classCode(),context.className(),
                            context.subjectCode(),context.subjectName(),List.copyOf(templates));
                }
                connection.commit();
                return new AssignmentEditPage(context,AssignmentEditSupport.form(detail.assignment(),questions));
            } catch(SQLException|RuntimeException ex) {
                try{connection.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("The draft could not be loaded for editing.",ex); }
    }

    public void updateAssignment(long teacherId, long assignmentId, AssignmentForm form) {
        if(teacherId<=0) throw new SecurityException("An active Teacher account is required.");
        if(assignmentId<=0) throw new IllegalArgumentException("Invalid assignment.");
        try(Connection connection=connections.open()) {
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            try {
                if(!dao.lockActiveTeacher(connection,teacherId)) throw new SecurityException("An active Teacher account is required.");
                Assignment stored=dao.lockOwnedAssignment(connection,teacherId,assignmentId);
                var context=dao.findCreatePage(connection,teacherId,stored.classId(),true);
                requireDraft(stored);
                var existing=dao.findAllDraftQuestions(connection,assignmentId,true);
                if(!form.value("revision").matches("[0-9a-f]{64}"))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("revision","Reopen Edit before saving this draft."));
                if(!AssignmentEditSupport.revision(stored,existing).equals(form.value("revision")))
                    throw new AssignmentEditConflictException("This draft changed after you opened Edit. Reload the latest draft before saving.");
                var draft=AssignmentEditSupport.validate(form,existing);
                if(draft.assignment().classId()!=stored.classId())
                    throw new AssignmentDraftValidator.ValidationException(Map.of("classId","An assignment cannot be moved to another class."));
                if(draft.assignment().templateId()!=stored.templateId()
                        && !dao.lockActiveTemplate(connection,draft.assignment().templateId(),context.subjectId()))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("templateId","Select an active template for this class's subject."));
                dao.updateDraft(connection,assignmentId,draft.assignment());
                dao.replaceDraftQuestions(connection,assignmentId,existing,draft.questions());
                connection.commit();
            } catch(SQLException|RuntimeException ex) {
                try{connection.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("The draft changes could not be saved.",ex); }
    }

    private static void requireDraft(Assignment a) {
        if(a.status()!=AssignmentStatus.DRAFT)
            throw new AssignmentEditConflictException("Only Draft assignments can be edited. This assignment is now "+a.status().name().toLowerCase(java.util.Locale.ROOT)+".");
    }

    public long previewTeacherId() {
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            return dao.findPreviewTeacherId(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Local preview Teacher could not be loaded.", ex);
        }
    }

    public AssignmentListPage listPage(long teacherId, Long classId, AssignmentListFilter filter) {
        if (teacherId <= 0) throw new SecurityException("An active Teacher account is required.");
        if (classId != null && classId <= 0) throw new IllegalArgumentException("Class ID must be a positive number.");
        Objects.requireNonNull(filter, "Assignment filters are required.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if (!dao.isActiveTeacher(connection, teacherId))
                    throw new SecurityException("An active Teacher account is required.");
                var classes = dao.findClassesForTeacher(connection, teacherId);
                if (classId != null && classes.stream().noneMatch(c -> c.classId() == classId))
                    throw new SecurityException("You cannot access assignments for this class.");
                Long selected = classId != null ? classId : classes.isEmpty() ? null : classes.get(0).classId();
                List<Assignment> assignments = selected == null ? List.of()
                        : dao.findForTeacher(connection, teacherId, selected, filter);
                connection.commit();
                return new AssignmentListPage(classes, selected, filter, assignments);
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Assignments could not be loaded.", ex);
        }
    }

    public java.util.List<Assignment> listAssignments(long teacherId, long classId) {
        return listPage(teacherId, classId, AssignmentListFilter.from(null, null, null, null)).assignments();
    }
    public AssignmentDetailPage detailPage(long teacherId, long assignmentId) {
        if (teacherId <= 0) throw new SecurityException("An active Teacher account is required.");
        if (assignmentId <= 0) throw new IllegalArgumentException("Assignment ID must be a positive number.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if (!dao.isActiveTeacher(connection, teacherId))
                    throw new SecurityException("An active Teacher account is required.");
                var page = dao.findDetailForTeacher(connection, teacherId, assignmentId)
                        .orElseThrow(() -> new NoSuchElementException("Assignment not found or unavailable."));
                var questions = page.assignment().responseType() == ResponseType.OBJECTIVE
                        ? dao.findQuestionsForTeacher(connection, teacherId, assignmentId) : List.<Question>of();
                connection.commit();
                return new AssignmentDetailPage(page.assignment(), page.classCode(), page.className(),
                        page.subjectCode(), page.subjectName(), page.termCode(), page.templateTitle(), questions);
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Assignment details could not be loaded.", ex);
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
                if(!dao.isActiveTeacher(connection,teacherId)) throw new SecurityException("An active Teacher account is required.");
                var detail=dao.findDetailForTeacher(connection,teacherId,assignmentId)
                        .orElseThrow(()->new NoSuchElementException("Assignment not found or unavailable."));
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
        } catch(SQLException ex) { throw new IllegalStateException("The assignment confirmation could not be loaded.",ex); }
    }

    public void publishAssignment(long teacherId,long assignmentId,String revision) {
        changeLifecycle(teacherId,assignmentId,revision,"publish");
    }

    public int cancelAssignment(long teacherId,long assignmentId,String revision) {
        return changeLifecycle(teacherId,assignmentId,revision,"cancel");
    }

    private int changeLifecycle(long teacherId,long assignmentId,String revision,String action) {
        checkLifecycleInputs(teacherId,assignmentId,action);
        try(Connection connection=connections.open()) {
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            try {
                if(!dao.lockActiveTeacher(connection,teacherId)) throw new SecurityException("An active Teacher account is required.");
                var a=dao.lockOwnedAssignment(connection,teacherId,assignmentId);
                dao.findCreatePage(connection,teacherId,a.classId(),true);
                boolean publishing="publish".equals(action);
                if((publishing && a.status()==AssignmentStatus.PUBLISHED)
                        || (!publishing && a.status()==AssignmentStatus.CANCELLED)) {
                    // Repeated confirmations keep the original timestamps and outcome.
                    connection.commit(); return 0;
                }
                if(publishing && a.status()!=AssignmentStatus.DRAFT)
                    throw new AssignmentEditConflictException("A cancelled assignment cannot be published.");
                var questions=dao.findAllDraftQuestions(connection,assignmentId,true);
                if(revision==null || !revision.matches("[0-9a-f]{64}"))
                    throw new AssignmentDraftValidator.ValidationException(Map.of("revision","Reopen the confirmation before continuing."));
                if(!AssignmentEditSupport.revision(a,questions).equals(revision))
                    throw new AssignmentEditConflictException("This assignment changed after you opened the confirmation. Review the latest saved content before confirming again.");
                var now=dao.currentTime(connection);
                int voided=0;
                if(publishing) {
                    var errors=AssignmentPublicationValidator.errors(a,questions,now);
                    if(!errors.isEmpty()) throw new AssignmentDraftValidator.ValidationException(errors);
                    dao.markPublished(connection,assignmentId,now);
                } else {
                    dao.markCancelled(connection,assignmentId,now);
                    voided=dao.voidInProgressSubmissions(connection,assignmentId);
                }
                connection.commit(); return voided;
            } catch(SQLException|RuntimeException ex) {
                try { connection.rollback(); } catch(SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("The assignment action could not be completed. Check its current status before retrying.",ex); }
    }

    private static void checkLifecycleInputs(long teacherId,long assignmentId,String action) {
        if(teacherId<=0) throw new SecurityException("An active Teacher account is required.");
        if(assignmentId<=0) throw new IllegalArgumentException("Invalid assignment.");
        if(!"publish".equals(action) && !"cancel".equals(action)) throw new IllegalArgumentException("Invalid assignment action.");
    }
}
