package vn.edu.fpt.ams.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import vn.edu.fpt.ams.dto.AssignmentListFilter;

/** Converts stored content into plain text values; the JSP escapes them when rendering. */
public final class AssignmentDetailView {
    private AssignmentDetailView() {}

    public record Checklist(List<String> items, boolean invalid) {}
    public record Option(String id, String text, boolean correct) {}
    public record Options(List<Option> items, boolean invalid) {}

    public static Checklist checklist(String json) {
        List<String> items = new ArrayList<>();
        if (json == null || json.isBlank()) return new Checklist(List.of(), false);
        boolean invalid = false;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return new Checklist(List.of(), true);
            for (var item : orderedItems(root, "task_order")) {
                String text = string(item);
                if (item.isJsonObject()) {
                    for (String key : List.of("title", "text", "task", "description")) {
                        text = string(item.getAsJsonObject().get(key));
                        if (text != null && !text.isBlank()) break;
                    }
                    String description = string(item.getAsJsonObject().get("description"));
                    if (text != null && description != null && !description.isBlank() && !description.equals(text))
                        text += "\n" + description;
                }
                if (text == null || text.isBlank()) invalid = true;
                else items.add(text);
            }
        } catch (RuntimeException ex) {
            invalid = true;
        }
        return new Checklist(List.copyOf(items), invalid);
    }

    public static Options options(String json) {
        List<Option> items = new ArrayList<>();
        if (json == null || json.isBlank()) return new Options(List.of(), false);
        boolean invalid = false;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return new Options(List.of(), true);
            for (var item : orderedItems(root, "option_order")) {
                if (!item.isJsonObject()) { invalid = true; continue; }
                JsonObject object = item.getAsJsonObject();
                JsonElement id = object.get("option_id");
                String text = string(object.get("option_text"));
                JsonElement correct = object.get("is_correct");
                if (id == null || !id.isJsonPrimitive()
                        || !(id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber())
                        || id.getAsString().isBlank() || text == null || text.isBlank()
                        || correct == null || !correct.isJsonPrimitive() || !correct.getAsJsonPrimitive().isBoolean()) {
                    invalid = true;
                    continue;
                }
                items.add(new Option(id.getAsString(), text, correct.getAsBoolean()));
            }
        } catch (RuntimeException ex) {
            invalid = true;
        }
        return new Options(List.copyOf(items), invalid);
    }

    private record OrderedItem(JsonElement item, int order) {}

    private static List<JsonElement> orderedItems(JsonElement root, String orderField) {
        List<OrderedItem> ordered = new ArrayList<>();
        int position = 0;
        for (JsonElement item : root.getAsJsonArray()) {
            position++;
            JsonElement order = item.isJsonObject() ? item.getAsJsonObject().get(orderField) : null;
            ordered.add(new OrderedItem(item, order(order, position)));
        }
        // Stable sorting preserves array order for ties, including legacy fallback positions.
        ordered.sort(Comparator.comparingInt(OrderedItem::order));
        return ordered.stream().map(OrderedItem::item).toList();
    }

    private static int order(JsonElement value, int fallback) {
        if (value != null && value.isJsonPrimitive()
                && (value.getAsJsonPrimitive().isNumber() || value.getAsJsonPrimitive().isString())) {
            String text = value.getAsString();
            if (text.matches("[1-9][0-9]*")) {
                try { return Integer.parseInt(text); }
                catch (NumberFormatException ignored) { }
            }
        }
        return fallback;
    }

    private static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    public static String listUrl(String base, long classId, AssignmentListFilter filter) {
        String url = base + "/teacher/assignments?classId=" + classId;
        if (!filter.search().isEmpty()) url += "&search=" + URLEncoder.encode(filter.search(), StandardCharsets.UTF_8);
        if (filter.purpose() != null) url += "&purpose=" + filter.purpose().name();
        if (filter.responseType() != null) url += "&responseType=" + filter.responseType().name();
        if (filter.status() != null) url += "&status=" + filter.status().name();
        return url;
    }
}
