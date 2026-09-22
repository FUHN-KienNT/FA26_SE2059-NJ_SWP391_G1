package vn.edu.fpt.ams.dto;

/** Import summary; no result is returned until the operation commits. */
public record ImportResultDTO(int imported, int skipped, int rejected) {}
