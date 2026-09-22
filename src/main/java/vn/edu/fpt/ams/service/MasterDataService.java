package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class MasterDataService {
    private final AcademicTermDAO dao = new AcademicTermDAO();

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<AcademicTerm> listTerms(String keyword, Boolean active) { return dao.findAll(keyword, active); }
    public long createTerm(AcademicTerm term) { ValidationUtil.laterThan(term.endsAt(), term.startsAt(), "Term end"); throw pending(); }
    public void updateTerm(AcademicTerm term) { throw pending(); }
    public void markCurrent(long id) { throw pending(); }
    public void setTermStatus(long id, boolean active) { throw pending(); }
}
