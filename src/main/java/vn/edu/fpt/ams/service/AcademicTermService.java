package vn.edu.fpt.ams.service;

import java.util.List;
import vn.edu.fpt.ams.model.AcademicTerm;

/** Academic Term operations from the Master Data class diagram. */
public class AcademicTermService {
    private final MasterDataService masterData = new MasterDataService();
    public List<AcademicTerm> list(String keyword, Boolean active) { return masterData.listTerms(keyword, active); }
    public long create(AcademicTerm term) { return masterData.createTerm(term); }
    public void update(AcademicTerm term) { masterData.updateTerm(term); }
    public void markCurrent(long id) { masterData.markCurrent(id); }
    public void setStatus(long id, boolean active) { masterData.setTermStatus(id, active); }
}
