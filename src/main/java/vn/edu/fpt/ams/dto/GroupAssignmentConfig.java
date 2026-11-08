package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;

/** Teacher-configured audience and scoring policy; group membership is frozen at publication. */
public record GroupAssignmentConfig(
        String deliveryMode, String targetMode, BigDecimal teacherWeight, BigDecimal peerWeight,
        long revision, List<Long> selectedGroupIds) {
    public GroupAssignmentConfig {
        if (!Set.of("INDIVIDUAL", "GROUP").contains(deliveryMode)
                || !Set.of("ALL", "SELECTED").contains(targetMode)
                || teacherWeight == null || peerWeight == null
                || teacherWeight.signum() < 0 || peerWeight.signum() < 0
                || teacherWeight.compareTo(new BigDecimal("100")) > 0
                || peerWeight.compareTo(new BigDecimal("100")) > 0
                || teacherWeight.stripTrailingZeros().scale() > 2
                || peerWeight.stripTrailingZeros().scale() > 2
                || teacherWeight.add(peerWeight).compareTo(new BigDecimal("100")) != 0
                || revision < 0)
            throw new IllegalArgumentException("Cấu hình giao bài hoặc hệ số điểm không hợp lệ.");

        teacherWeight = teacherWeight.setScale(2);
        peerWeight = peerWeight.setScale(2);
        selectedGroupIds = List.copyOf(selectedGroupIds);
        if (selectedGroupIds.size() > 5000
                || selectedGroupIds.stream().anyMatch(id -> id == null || id <= 0)
                || selectedGroupIds.stream().distinct().count() != selectedGroupIds.size())
            throw new IllegalArgumentException("Danh sách nhóm không hợp lệ.");
    }

    public static GroupAssignmentConfig individual() {
        return new GroupAssignmentConfig("INDIVIDUAL", "ALL", new BigDecimal("70"),
                new BigDecimal("30"), 0, List.of());
    }

    public boolean group() { return "GROUP".equals(deliveryMode); }

    public GroupAssignmentConfig forGroups(List<Long> groupIds, long expectedRevision) {
        return new GroupAssignmentConfig(deliveryMode, targetMode, teacherWeight, peerWeight,
                expectedRevision, groupIds);
    }

    public AssignmentForm fill(AssignmentForm form) {
        var values = new HashMap<>(form.values());
        values.put("deliveryMode", deliveryMode);
        values.put("groupTargetMode", targetMode);
        values.put("teacherWeight", teacherWeight.stripTrailingZeros().toPlainString());
        values.put("peerWeight", peerWeight.stripTrailingZeros().toPlainString());
        values.put("groupRevision", Long.toString(revision));
        values.put("selectedGroupIds", String.join(",", selectedGroupIds.stream().map(String::valueOf).toList()));
        return new AssignmentForm(values);
    }

    public static GroupAssignmentConfig from(AssignmentForm form) {
        var errors = new HashMap<String, String>();
        String mode = form.value("deliveryMode").isBlank() ? "INDIVIDUAL" : form.value("deliveryMode");
        String targets = form.value("groupTargetMode").isBlank() ? "ALL" : form.value("groupTargetMode");
        if (!Set.of("INDIVIDUAL", "GROUP").contains(mode))
            errors.put("deliveryMode", "Chọn giao bài cá nhân hoặc theo nhóm.");
        if (!Set.of("ALL", "SELECTED").contains(targets))
            errors.put("groupTargetMode", "Chọn tất cả nhóm hoặc các nhóm cụ thể.");

        long revision = 0;
        try {
            String raw = form.value("groupRevision");
            if (!raw.isBlank()) {
                if (!raw.matches("[0-9]+")) throw new NumberFormatException();
                revision = Long.parseLong(raw);
            }
        } catch (NumberFormatException ex) {
            errors.put("groupRevision", "Hãy mở lại bản nháp để tải cấu hình nhóm mới nhất.");
        }

        // Individual work retains its existing teacher-only scoring behavior.
        BigDecimal teacher = new BigDecimal("70"), peer = new BigDecimal("30");
        List<Long> ids = new ArrayList<>();
        if ("GROUP".equals(mode)) {
            teacher = weight(form.value("teacherWeight"), "teacherWeight", errors);
            peer = weight(form.value("peerWeight"), "peerWeight", errors);
            if (teacher.add(peer).compareTo(new BigDecimal("100")) != 0)
                errors.put("teacherWeight", "Tổng hệ số giảng viên và sinh viên phải bằng 100%.");

            if ("SELECTED".equals(targets)) {
                try {
                    String raw = form.value("selectedGroupIds");
                    if (raw.isBlank()) throw new IllegalArgumentException();
                    String[] parts = raw.split(",", -1);
                    if (parts.length > 5000) throw new IllegalArgumentException();
                    for (String part : parts) {
                        if (!part.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
                        long id = Long.parseLong(part);
                        if (!ids.contains(id)) ids.add(id);
                    }
                } catch (IllegalArgumentException ex) {
                    errors.put("selectedGroupIds", "Chọn ít nhất một nhóm hợp lệ cho mỗi lớp được giao.");
                }
            }
        } else targets = "ALL";

        if (!errors.isEmpty()) throw new ValidationException(Map.copyOf(errors));
        return new GroupAssignmentConfig(mode, targets, teacher, peer, revision, ids);
    }

    private static BigDecimal weight(String raw, String field, Map<String, String> errors) {
        if (!raw.matches("[0-9]{1,3}(?:\\.[0-9]{1,2})?")) {
            errors.put(field, "Nhập hệ số từ 0 đến 100, tối đa hai chữ số thập phân.");
            return BigDecimal.ZERO;
        }
        BigDecimal value = new BigDecimal(raw);
        if (value.compareTo(new BigDecimal("100")) > 0)
            errors.put(field, "Hệ số không được vượt quá 100%.");
        return value;
    }
}
