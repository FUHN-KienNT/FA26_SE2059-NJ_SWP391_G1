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
    public long createSubject(SubjectDTO request, java.util.Set<Long> expertIds) { ValidationUtil.required(request.subjectCode(), "subject code"); ValidationUtil.required(request.subjectName(), "subject name"); if (expertIds == null || expertIds.isEmpty()) throw new IllegalArgumentException("At least one Expert is required"); throw pending(); }
    public void updateSubject(SubjectDTO request) { throw pending(); }
    public void assignExperts(long subjectId, java.util.Set<Long> expertIds) { throw pending(); }
    public void changeStatus(long subjectId, SubjectStatus status) { throw pending(); }
    public void openForTerm(SubjectTerm term) { ValidationUtil.laterThan(term.registrationClosesAt(), term.registrationOpensAt(), "Registration close"); throw pending(); }
    private SubjectDTO toDTO(Subject s) { return new SubjectDTO(s.subjectId(), s.subjectCode(), s.subjectName(), s.description(), s.status()); }
}
