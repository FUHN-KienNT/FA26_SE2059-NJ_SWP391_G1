package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class SubjectService {
    private final SubjectDAO subjectDAO = new SubjectDAO();
    private final SubjectExpertDAO subjectExpertDAO = new SubjectExpertDAO(); private final SubjectTermDAO subjectTermDAO = new SubjectTermDAO();
    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<SubjectDTO> listSubjects(String keyword, SubjectStatus status) { return subjectDAO.findAll(keyword, status).stream().map(this::toDTO).toList(); }
    public SubjectDTO findSubject(long id) { return subjectDAO.findById(id).map(this::toDTO).orElseThrow(); }
    public long createSubject(SubjectDTO request, java.util.Map<Long, ResponsibilityType> experts) { ValidationUtil.required(request.subjectCode(), "subject code"); ValidationUtil.required(request.subjectName(), "subject name"); validateExpertCounts(experts); throw pending(); }
    public void updateSubject(SubjectDTO request) { throw pending(); }
    public void assignExperts(long subjectId, java.util.Map<Long, ResponsibilityType> experts) { validateExpertCounts(experts); throw pending(); }
    public void changeStatus(long subjectId, SubjectStatus status) {
        if (status == SubjectStatus.ACTIVE) {
            java.util.Map<Long, ResponsibilityType> activeExperts = subjectExpertDAO.findBySubjectId(subjectId)
                    .stream().filter(SubjectExpert::active)
                    .collect(java.util.stream.Collectors.toMap(SubjectExpert::expertId,
                            SubjectExpert::responsibilityType));
            validateExpertCounts(activeExperts);
        }
        throw pending();
    }
    public void openForTerm(SubjectTerm term) { ValidationUtil.laterThan(term.registrationClosesAt(), term.registrationOpensAt(), "Registration close"); throw pending(); }
    private static void validateExpertCounts(java.util.Map<Long, ResponsibilityType> experts) {
        if (experts == null) throw new IllegalArgumentException("Expert assignments are required");
        long primaryCount = experts.values().stream().filter(ResponsibilityType.PRIMARY::equals).count();
        long supportingCount = experts.values().stream().filter(ResponsibilityType.SUPPORTING::equals).count();
        if (primaryCount < 1 || primaryCount > 2) throw new IllegalArgumentException("An active Subject requires 1 to 2 PRIMARY Experts");
        if (supportingCount > 2) throw new IllegalArgumentException("An active Subject allows at most 2 SUPPORTING Experts");
    }
    private SubjectDTO toDTO(Subject s) { return new SubjectDTO(s.subjectId(), s.subjectCode(), s.subjectName(), s.description(), s.status()); }
}
