package vn.edu.fpt.ams.service;

import java.util.Collection;
import vn.edu.fpt.ams.model.SettingType;
import vn.edu.fpt.ams.model.SystemSetting;

/** Supported new Assignment combinations; saved Assignments retain historical values. */
public final class AssignmentSettingRules {
  private AssignmentSettingRules() {}

  public static boolean hasAvailableCombination(Collection<SystemSetting> choices) {
    return available(choices, SettingType.ASSIGNMENT_TYPE, "REGULAR")
            && (available(choices, SettingType.RESPONSE_TYPE, "ESSAY")
                || available(choices, SettingType.RESPONSE_TYPE, "OBJECTIVE"))
        || available(choices, SettingType.ASSIGNMENT_TYPE, "PRACTICE")
            && available(choices, SettingType.RESPONSE_TYPE, "OBJECTIVE");
  }

  private static boolean available(Collection<SystemSetting> choices, SettingType type, String value) {
    return choices.stream().anyMatch(choice -> choice.active()
        && choice.type() == type && value.equals(choice.value()));
  }
}
