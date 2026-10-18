package vn.edu.fpt.ams.dto;
import java.util.List;
import vn.edu.fpt.ams.model.AssignmentTemplate;
public record TemplatePage(List<AssignmentTemplate> items, long total, int page, int pageSize) {
  public long pages() { return Math.max(1, (total + pageSize - 1) / pageSize); }
}
