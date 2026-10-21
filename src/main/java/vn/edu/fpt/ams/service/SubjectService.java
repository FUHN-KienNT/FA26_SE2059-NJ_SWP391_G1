package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import vn.edu.fpt.ams.dao.MasterDataDAO;
import vn.edu.fpt.ams.dao.SubjectDAO;
import vn.edu.fpt.ams.dao.SubjectExpertDAO;
import vn.edu.fpt.ams.dao.SubjectTermDAO;
import vn.edu.fpt.ams.dto.ExpertOptionDTO;
import vn.edu.fpt.ams.dto.SubjectDTO;
import vn.edu.fpt.ams.dto.SubjectDetailsDTO;
import vn.edu.fpt.ams.dto.SubjectExpertDTO;
import vn.edu.fpt.ams.dto.SubjectSummaryDTO;
import vn.edu.fpt.ams.dto.SubjectTermViewDTO;
import vn.edu.fpt.ams.model.AcademicTerm;
import vn.edu.fpt.ams.model.ResponsibilityType;
import vn.edu.fpt.ams.model.Subject;
import vn.edu.fpt.ams.model.SubjectExpert;
import vn.edu.fpt.ams.model.SubjectStatus;
import vn.edu.fpt.ams.model.SubjectTerm;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;
import vn.edu.fpt.ams.util.ValidationUtil;


public class SubjectService {
  private static final int PAGE_SIZE = 20;
  private final Jdbc.Connections connections;
  private final Clock clock;
  private final SubjectDAO subjects = new SubjectDAO();
  private final SubjectExpertDAO subjectExperts = new SubjectExpertDAO();
  private final SubjectTermDAO subjectTerms = new SubjectTermDAO();
  private final MasterDataDAO masterData;

  public SubjectService() {
    this(DBConnection::getConnection);
  }

  public SubjectService(Jdbc.Connections source) {
    this(source, Clock.systemUTC());
  }

  public SubjectService(Jdbc.Connections source, Clock clock) {
    connections = source;
    this.clock = java.util.Objects.requireNonNull(clock);
    this.masterData = new MasterDataDAO(clock);
  }

  public List<SubjectSummaryDTO> list(
      long actor, String keyword, SubjectStatus status, int page) {
    return list(actor, keyword, status, null, page);
  }

  public List<SubjectSummaryDTO> list(
      long actor, String keyword, SubjectStatus status, Long termId, int page) {
    validateSearch(keyword);
    if (termId != null) requirePositive(termId, "Mã học kỳ");
    if (page < 1) throw new IllegalArgumentException("Số trang không hợp lệ.");
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          var result = new ArrayList<SubjectSummaryDTO>();
          var pageSubjects = subjects.search(c, keyword, status, termId, page, PAGE_SIZE);
          var terms = termViews(c, pageSubjects);
          for (Subject subject : pageSubjects)
            result.add(summary(c, subject, terms.getOrDefault(subject.subjectId(), List.of())));
          return result;
        });
  }

  public long count(long actor, String keyword, SubjectStatus status) {
    return count(actor, keyword, status, null);
  }

  public long count(long actor, String keyword, SubjectStatus status, Long termId) {
    validateSearch(keyword);
    if (termId != null) requirePositive(termId, "Mã học kỳ");
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return subjects.count(c, keyword, status, termId);
        });
  }

  public List<AcademicTerm> termOptions(long actor) {
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      return masterData.findTerms(c, null, null);
    });
  }

  public SubjectDetailsDTO details(long actor, long subjectId) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          Subject subject = requireSubject(c, subjectId, false);
          List<SubjectExpertDTO> history = expertViews(c, subjectId, false);
          List<SubjectExpertDTO> active =
              history.stream().filter(SubjectExpertDTO::active).toList();
          List<SubjectExpertDTO> primary =
              active.stream()
                  .filter(x -> x.responsibilityType() == ResponsibilityType.PRIMARY)
                  .toList();
          List<SubjectExpertDTO> supporting =
              active.stream()
                  .filter(x -> x.responsibilityType() == ResponsibilityType.SUPPORTING)
                  .toList();
          var termViews = termViews(c, List.of(subject)).getOrDefault(subjectId, List.of());
          return new SubjectDetailsDTO(
              toDTO(subject), primary, supporting, history, List.copyOf(termViews));
        });
  }

  public List<ExpertOptionDTO> eligibleExperts(long actor, String keyword) {
    validateSearch(keyword);
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          String pattern =
              "%" + (keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT)) + "%";
          try (var p =
                  Jdbc.statement(
                      c,
                      "SELECT user_id,full_name,email FROM users WHERE role='EXPERT'"
                          + " AND account_status='ACTIVE' AND"
                          + " (lower(full_name) LIKE ? OR lower(email) LIKE ?)"
                          + " ORDER BY full_name,email",
                      pattern,
                      pattern);
              var r = p.executeQuery()) {
            var result = new ArrayList<ExpertOptionDTO>();
            while (r.next())
              result.add(new ExpertOptionDTO(r.getLong(1), r.getString(2), r.getString(3)));
            return result;
          }
        });
  }

  public List<AcademicTerm> eligibleTerms(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          Instant now = clock.instant();
          return masterData.findTerms(c, null, true).stream()
              .filter(term -> term.endsAt().isAfter(now)).toList();
        });
  }

  /** UC14: Subject and initial responsibilities commit together. */
  public long create(
      long actor, SubjectDTO request, Map<Long, ResponsibilityType> assignments) {
    return create(actor, request, assignments, null);
  }

  public record InitialTerm(long termId, boolean registrationEnabled,
      Instant registrationOpensAt, Instant registrationClosesAt) {}

  /** UC14 + optional UC17: the Subject, experts and opening share one transaction. */
  public long create(long actor, SubjectDTO request, Map<Long, ResponsibilityType> assignments,
      InitialTerm initialTerm) {
    if (request == null) throw new IllegalArgumentException("Vui lòng nhập thông tin môn học.");
    String code = subjectCode(request.subjectCode());
    String name = subjectName(request.subjectName());
    String description = description(request.description());
    if (request.status() == null) throw new IllegalArgumentException("Vui lòng chọn trạng thái ban đầu.");
    validateExpertCounts(assignments);
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          if (subjects.findByCode(c, code).isPresent())
            throw new IllegalArgumentException("Mã môn học đã tồn tại.");
          validateEligibleExperts(c, assignments.keySet());
          long id =
              subjects.insert(
                  c, new Subject(0, code, name, description, request.status()));
          for (var entry : assignments.entrySet())
            subjectExperts.assign(c, id, entry.getKey(), entry.getValue());
          if (initialTerm != null) {
            var opening = new SubjectTerm(0, id, initialTerm.termId(), initialTerm.registrationEnabled(),
                initialTerm.registrationOpensAt(), initialTerm.registrationClosesAt());
            validateTerm(opening);
            saveTerm(c, opening);
          }
          return id;
        });
  }

  /** UC15: code and status are immutable in this operation. */
  public void update(
      long actor, long subjectId, String submittedCode, String name, String description) {
    String code = subjectCode(submittedCode);
    String validName = subjectName(name);
    String validDescription = description(description);
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          Subject old = requireSubject(c, subjectId, true);
          if (!old.subjectCode().equals(code))
            throw new IllegalArgumentException("Không thể thay đổi mã môn học.");
          subjects.update(
              c,
              new Subject(
                  old.subjectId(),
                  old.subjectCode(),
                  validName,
                  validDescription,
                  old.status()));
          return null;
        });
  }

  /** UC15 function: all responsibility changes commit or roll back together. */
  public void assignExperts(
      long actor, long subjectId, Map<Long, ResponsibilityType> assignments) {
    validateExpertCounts(assignments);
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          requireSubject(c, subjectId, true);
          validateEligibleExperts(c, assignments.keySet());
          synchronizeExperts(c, subjectId, assignments);
          return null;
        });
  }

  /** UC16 preserves the Subject and history; deactivation also closes new registration. */
  public void changeStatus(long actor, long subjectId, SubjectStatus target) {
    if (target == null) throw new IllegalArgumentException("Vui lòng chọn trạng thái môn học.");
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          Subject current = requireSubject(c, subjectId, true);
          if (current.status() == target)
            throw new IllegalArgumentException("Môn học đã ở trạng thái này.");
          if (target == SubjectStatus.ACTIVE) {
            Map<Long, ResponsibilityType> assignments =
                subjectExperts.findBySubjectId(c, subjectId, true).stream()
                    .filter(SubjectExpert::active)
                    .collect(
                        Collectors.toMap(
                            SubjectExpert::expertId, SubjectExpert::responsibilityType));
            validateExpertCounts(assignments);
            validateEligibleExperts(c, assignments.keySet());
          } else {
            Jdbc.update(
                c,
                "UPDATE subject_terms SET registration_enabled=FALSE WHERE subject_id=?",
                subjectId);
          }
          subjects.updateStatus(c, subjectId, target);
          return null;
        });
  }

  /** UC17 updates the stable Subject-Term row when the pair already exists. */
  public long openForTerm(long actor, SubjectTerm requested) {
    validateTerm(requested);
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      return saveTerm(c, requested);
    });
  }

  private static void validateTerm(SubjectTerm requested) {
    if (requested == null) throw new IllegalArgumentException("Vui lòng nhập cấu hình học kỳ cho môn học.");
    requirePositive(requested.subjectId(), "Mã môn học");
    requirePositive(requested.termId(), "Mã học kỳ");
    if (requested.registrationOpensAt() == null
        || requested.registrationClosesAt() == null)
      throw new IllegalArgumentException("Vui lòng nhập thời gian mở và đóng đăng ký.");
    if (!requested.registrationClosesAt().isAfter(requested.registrationOpensAt()))
      throw new IllegalArgumentException("Thời gian đóng đăng ký phải sau thời gian mở đăng ký.");
  }

  private long saveTerm(Connection c, SubjectTerm requested) throws SQLException {
    Subject subject = requireSubject(c, requested.subjectId(), true);
    if (subject.status() != SubjectStatus.ACTIVE)
      throw new IllegalArgumentException("Chỉ có thể mở môn học đang hoạt động theo học kỳ.");
    AcademicTerm term =
        masterData
            .findTermById(c, requested.termId(), true)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy học kỳ."));
    if (!term.active())
      throw new IllegalArgumentException("Vui lòng chọn học kỳ đang hoạt động.");
    if (!term.endsAt().isAfter(clock.instant()))
      throw new IllegalArgumentException("Học kỳ đã kết thúc; chỉ được xem lịch sử, không thể sửa đăng ký.");
    Optional<SubjectTerm> existing =
        subjectTerms.findBySubjectAndTerm(c, subject.subjectId(), term.termId(), true);
    if (existing.isPresent()) {
      SubjectTerm old = existing.get();
      subjectTerms.update(
          c,
          new SubjectTerm(
              old.subjectTermId(),
              subject.subjectId(),
              term.termId(),
              requested.registrationEnabled(),
              requested.registrationOpensAt(),
              requested.registrationClosesAt()));
      return old.subjectTermId();
    }
    return subjectTerms.insert(
        c,
        new SubjectTerm(
            0,
            subject.subjectId(),
            term.termId(),
            requested.registrationEnabled(),
            requested.registrationOpensAt(),
            requested.registrationClosesAt()));
  }

  private void synchronizeExperts(
      Connection c, long subjectId, Map<Long, ResponsibilityType> desired)
      throws SQLException {
    Map<Long, SubjectExpert> existing =
        subjectExperts.findBySubjectId(c, subjectId, true).stream()
            .collect(Collectors.toMap(SubjectExpert::expertId, x -> x));
    for (SubjectExpert old : existing.values()) {
      ResponsibilityType next = desired.get(old.expertId());
      if (old.active() && next == null) {
        if (Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM class_experts WHERE subject_id=? AND expert_id=?"
                    + " AND is_active=TRUE",
                subjectId,
                old.expertId())
            > 0)
          throw new IllegalArgumentException(
              "Vui lòng gỡ phân công chuyên gia ở lớp đang hoạt động trước.");
        subjectExperts.deactivate(c, subjectId, old.expertId());
      } else if (next != null
          && (!old.active() || old.responsibilityType() != next)) {
        subjectExperts.assign(c, subjectId, old.expertId(), next);
      }
    }
    for (var entry : desired.entrySet())
      if (!existing.containsKey(entry.getKey()))
        subjectExperts.assign(c, subjectId, entry.getKey(), entry.getValue());
  }

  private Map<Long, List<SubjectTermViewDTO>> termViews(Connection c, List<Subject> pageSubjects)
      throws SQLException {
    var byId = pageSubjects.stream().collect(Collectors.toMap(Subject::subjectId, s -> s));
    var result = new LinkedHashMap<Long, List<SubjectTermViewDTO>>();
    Instant now = clock.instant();
    for (var row : subjectTerms.findBySubjects(c, pageSubjects.stream().map(Subject::subjectId).toList())) {
      var configured = row.configuration();
      var term = row.academicTerm();
      result.computeIfAbsent(configured.subjectId(), id -> new ArrayList<>()).add(
          new SubjectTermViewDTO(configured.subjectTermId(), term.termId(), term.termCode(), term.termName(),
              configured.registrationEnabled(), configured.registrationOpensAt(), configured.registrationClosesAt(),
              registrationStatus(byId.get(configured.subjectId()), term, configured, now), term.active(), term.current()));
    }
    return result;
  }

  private SubjectSummaryDTO summary(Connection c, Subject subject, List<SubjectTermViewDTO> terms) throws SQLException {
    List<SubjectExpertDTO> active = expertViews(c, subject.subjectId(), true);
    return new SubjectSummaryDTO(
        subject.subjectId(),
        subject.subjectCode(),
        subject.subjectName(),
        subject.status(),
        active.stream()
            .filter(x -> x.responsibilityType() == ResponsibilityType.PRIMARY)
            .toList(),
        active.stream()
            .filter(x -> x.responsibilityType() == ResponsibilityType.SUPPORTING)
            .toList(), List.copyOf(terms));
  }

  private List<SubjectExpertDTO> expertViews(
      Connection c, long subjectId, boolean activeOnly) throws SQLException {
    String sql =
        "SELECT se.subject_expert_id,se.expert_id,u.full_name,u.email,"
            + "se.responsibility_type,se.is_active FROM subject_experts se"
            + " JOIN users u ON u.user_id=se.expert_id WHERE se.subject_id=?"
            + (activeOnly ? " AND se.is_active=TRUE" : "")
            + " ORDER BY se.responsibility_type,u.full_name";
    try (var p = Jdbc.statement(c, sql, subjectId); var r = p.executeQuery()) {
      var result = new ArrayList<SubjectExpertDTO>();
      while (r.next())
        result.add(
            new SubjectExpertDTO(
                r.getLong(1),
                r.getLong(2),
                r.getString(3),
                r.getString(4),
                ResponsibilityType.valueOf(r.getString(5)),
                r.getBoolean(6)));
      return result;
    }
  }

  private static void validateEligibleExperts(Connection c, Set<Long> ids)
      throws SQLException {
    if (ids.stream().anyMatch(x -> x == null || x <= 0))
      throw new IllegalArgumentException("Mã chuyên gia không hợp lệ.");
    if (ids.isEmpty()) return;
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    long eligible =
        Jdbc.scalar(
            c,
            "SELECT COUNT(*) FROM users WHERE user_id IN ("
                + placeholders
                + ") AND role='EXPERT' AND account_status='ACTIVE'",
            ids.toArray());
    if (eligible != ids.size())
      throw new IllegalArgumentException("Chỉ có thể phân công tài khoản chuyên gia đang hoạt động.");
  }

  public static void validateExpertCounts(
      Map<Long, ResponsibilityType> assignments) {
    if (assignments == null)
      throw new IllegalArgumentException("Vui lòng chọn phân công chuyên gia.");
    if (assignments.entrySet().stream()
        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null))
      throw new IllegalArgumentException("Vui lòng chọn chuyên gia và vai trò phụ trách.");
    long primary =
        assignments.values().stream()
            .filter(ResponsibilityType.PRIMARY::equals)
            .count();
    long supporting =
        assignments.values().stream()
            .filter(ResponsibilityType.SUPPORTING::equals)
            .count();
    if (primary < 1 || primary > 2)
      throw new IllegalArgumentException(
          "Môn học cần có từ 1 đến 2 chuyên gia chính.");
    if (supporting > 2)
      throw new IllegalArgumentException(
          "Môn học chỉ được có tối đa 2 chuyên gia hỗ trợ.");
  }

  private Subject requireSubject(Connection c, long subjectId, boolean lock)
      throws SQLException {
    requirePositive(subjectId, "Mã môn học");
    return subjects
        .findById(c, subjectId, lock)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy môn học."));
  }

  private static String registrationStatus(
      Subject subject, AcademicTerm academicTerm, SubjectTerm configured, Instant now) {
    if (subject.status() != SubjectStatus.ACTIVE) return "SUBJECT_INACTIVE";
    if (!academicTerm.active()) return "TERM_INACTIVE";
    if (!academicTerm.endsAt().isAfter(now)) return "TERM_ENDED";
    if (!configured.registrationEnabled()) return "DISABLED";
    if (now.isBefore(configured.registrationOpensAt())) return "UPCOMING";
    if (!now.isBefore(configured.registrationClosesAt())) return "CLOSED";
    return "OPEN";
  }

  private static void validateSearch(String keyword) {
    if (keyword != null && keyword.length() > 80)
      throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 80 ký tự.");
  }

  private static String subjectCode(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Vui lòng nhập mã môn học.");
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (normalized.length() > 30)
      throw new IllegalArgumentException("Mã môn học không được quá 30 ký tự.");
    return normalized;
  }

  private static String subjectName(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Vui lòng nhập tên môn học.");
    String normalized = value.strip();
    if (normalized.length() > 150)
      throw new IllegalArgumentException("Tên môn học không được quá 150 ký tự.");
    return normalized;
  }

  private static String description(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) throw new IllegalArgumentException(name + " không hợp lệ.");
  }

  private static SubjectDTO toDTO(Subject subject) {
    return new SubjectDTO(
        subject.subjectId(),
        subject.subjectCode(),
        subject.subjectName(),
        subject.description(),
        subject.status());
  }
}
