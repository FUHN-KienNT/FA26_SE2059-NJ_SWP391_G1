package vn.edu.fpt.ams.dto;

import java.util.List;

/** Summary and immutable row details for the completed per-student import operations. */
public record ImportResultDTO(int imported, int skipped, int rejected, List<ImportLineResult> lines) {
    /** Copy row outcomes so session results cannot change after import. */
    public ImportResultDTO { lines = List.copyOf(lines); }
}
