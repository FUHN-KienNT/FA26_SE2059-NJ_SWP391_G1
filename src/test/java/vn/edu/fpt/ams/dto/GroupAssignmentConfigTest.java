package vn.edu.fpt.ams.dto;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;

class GroupAssignmentConfigTest {
    private AssignmentForm group(String teacher, String peer) {
        return new AssignmentForm(Map.of("deliveryMode", "GROUP", "groupTargetMode", "SELECTED",
                "selectedGroupIds", "11,12", "teacherWeight", teacher, "peerWeight", peer, "groupRevision", "0"));
    }

    @Test void legacyFormsRemainIndividualAndNewFormsDefaultToSeventyThirty() {
        assertFalse(GroupAssignmentConfig.from(new AssignmentForm(Map.of())).group());
        var form = AssignmentForm.empty(10);
        assertEquals("70", form.value("teacherWeight"));
        assertEquals("30", form.value("peerWeight"));
        assertEquals("0", form.value("groupRevision"));
    }

    @Test void acceptsZeroAndHundredWeightsAndExactTwoDecimalPercentages() {
        assertEquals("0.00", GroupAssignmentConfig.from(group("0", "100")).teacherWeight().toPlainString());
        assertEquals("100.00", GroupAssignmentConfig.from(group("100", "0")).teacherWeight().toPlainString());
        assertEquals("70.25", GroupAssignmentConfig.from(group("70.25", "29.75")).teacherWeight().toPlainString());
    }

    @Test void rejectsExpressionsFractionsOutOfBoundsAndNonHundredTotals() {
        for (String teacher : new String[]{"5+65", "70.001", "-1", "101", "1e2", "", "70,5"})
            assertThrows(ValidationException.class, () -> GroupAssignmentConfig.from(group(teacher, "30")));
        assertThrows(ValidationException.class, () -> GroupAssignmentConfig.from(group("69.99", "30")));
        assertThrows(ValidationException.class, () -> GroupAssignmentConfig.from(group("0", "0")));
    }

    @Test void selectedIdsArePositiveAndAllModeIgnoresResidualCheckboxValues() {
        var values = new HashMap<>(group("70", "30").values());
        for (String value : new String[]{"", "0", "-1", "11,", "abc", "9223372036854775808"}) {
            values.put("selectedGroupIds", value);
            assertThrows(ValidationException.class, () -> GroupAssignmentConfig.from(new AssignmentForm(values)));
        }
        values.put("groupTargetMode", "ALL");
        assertTrue(GroupAssignmentConfig.from(new AssignmentForm(values)).selectedGroupIds().isEmpty());
    }

    @Test void rejectsInvalidModesAndStaleRevisionSyntax() {
        var values = new HashMap<>(group("70", "30").values());
        values.put("deliveryMode", "OTHER");
        assertThrows(ValidationException.class, () -> GroupAssignmentConfig.from(new AssignmentForm(values)));
        values.put("deliveryMode", "GROUP");
        values.put("groupRevision", "-1");
        assertThrows(ValidationException.class, () -> GroupAssignmentConfig.from(new AssignmentForm(values)));
    }

    @Test void formRoundTripPreservesAudienceWeightsAndRevision() {
        var config = GroupAssignmentConfig.from(group("70.25", "29.75")).forGroups(java.util.List.of(21L), 7);
        assertEquals(config, GroupAssignmentConfig.from(config.fill(AssignmentForm.empty(10))));
    }
}
