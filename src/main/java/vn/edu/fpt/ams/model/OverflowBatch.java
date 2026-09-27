package vn.edu.fpt.ams.model;

import java.time.Instant;

public record OverflowBatch(long overflowBatchId, long sourceClassId, long importedBy,
                            String sourceFileName, OverflowBatchStatus status,
                            String overflowStudents, Instant createdAt) {}
