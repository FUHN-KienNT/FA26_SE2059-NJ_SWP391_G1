package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class MasterDataService {
    private final MasterDataDAO dao = new MasterDataDAO();

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<AcademicTerm> listTerms(String keyword, Boolean active) { return dao.findTerms(keyword, active); }
    public java.util.Optional<AcademicTerm> getTerm(long termId) { return dao.findTermById(termId); }
    public long createTerm(AcademicTerm term) { ValidationUtil.laterThan(term.endsAt(), term.startsAt(), "Term end"); return dao.insertTerm(term); }
    public void updateTerm(AcademicTerm term) { ValidationUtil.laterThan(term.endsAt(), term.startsAt(), "Term end"); dao.updateTerm(term); }
    public void markCurrent(long id) { dao.markCurrentTerm(id); }
    public void setTermStatus(long id, boolean active) { dao.updateTermStatus(id, active); }
}
