package vn.edu.fpt.ams.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saved content reviewed before a Teacher confirms Publish or Cancel. */
public record AssignmentLifecyclePage(AssignmentDetailPage detail, String action,
        String revision, Map<String,String> errors, int inProgressCount) {
    public AssignmentLifecyclePage {
        errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
