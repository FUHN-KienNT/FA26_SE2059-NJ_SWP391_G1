package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record SettingDTO(String key, String value, String description, boolean active) {}
