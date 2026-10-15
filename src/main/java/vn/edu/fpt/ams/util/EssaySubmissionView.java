package vn.edu.fpt.ams.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pairs explicitly keyed Essay answers with frozen tasks. All strings still need HTML escaping. */
public final class EssaySubmissionView {
    private static final String FORMAT = "ams-essay-v1";
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private EssaySubmissionView() {}

    /** Order 0 is a readable legacy task without a reliable identity; it is never paired. */
    public record Item(int order, String title, String description, String answer) {}
    public record UnmatchedAnswer(String reference, String text) {}
    public record Essay(List<Item> items, String legacyContent, boolean structured,
                        boolean invalid, List<UnmatchedAnswer> unmatched) {
        public Essay {
            items = List.copyOf(items);
            unmatched = List.copyOf(unmatched);
        }
    }

    /**
     * New content: {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"..."}]}.
     * Plain text, including JSON-looking legacy work, remains one exact original block.
     * Answer order and paragraph breaks never determine which question an answer belongs to.
     */
    public static Essay read(String taskChecklistJson, String content) {
        Tasks tasks = tasks(taskChecklistJson);
        if (content == null || content.isBlank())
            return new Essay(tasks.items(), content, false, tasks.invalid(), List.of());

        JsonElement root;
        try {
            root = parse(content);
        } catch (RuntimeException ex) {
            String trimmed = content.stripLeading();
            boolean possibleJson = trimmed.startsWith("{") || trimmed.startsWith("[");
            return new Essay(tasks.items(), content, false, tasks.invalid() || possibleJson, List.of());
        }
        if (root == null || !root.isJsonObject()
                || !FORMAT.equals(text(root.getAsJsonObject().get("format"))))
            return new Essay(tasks.items(), content, false, tasks.invalid(), List.of());

        JsonElement answers = root.getAsJsonObject().get("answers");
        if (answers == null || !answers.isJsonArray())
            return new Essay(tasks.items(), content, false, true, List.of());

        List<SavedAnswer> saved = new ArrayList<>();
        Map<Integer, Integer> counts = new HashMap<>();
        List<UnmatchedAnswer> unmatched = new ArrayList<>();
        boolean invalid = tasks.invalid();
        int index = 0;
        for (JsonElement element : answers.getAsJsonArray()) {
            index++;
            if (!element.isJsonObject()) {
                unmatched.add(new UnmatchedAnswer("Answer " + index + " (invalid format)", element.toString()));
                invalid = true;
                continue;
            }
            var object = element.getAsJsonObject();
            int order = order(object.get("task_order"));
            if (order > 0) counts.merge(order, 1, Integer::sum);
            String answer = text(object.get("text"));
            if (order == 0 || answer == null) {
                unmatched.add(new UnmatchedAnswer("Answer " + index + " (invalid reference or text)",
                        answer == null ? element.toString() : answer));
                invalid = true;
            } else {
                saved.add(new SavedAnswer(order, answer));
            }
        }

        Map<Integer, String> paired = new HashMap<>();
        for (SavedAnswer answer : saved) {
            if (counts.get(answer.order()) == 1 && tasks.counts().getOrDefault(answer.order(), 0) == 1
                    && tasks.items().stream().anyMatch(item -> item.order() == answer.order())) {
                paired.put(answer.order(), answer.text());
            } else {
                unmatched.add(new UnmatchedAnswer("Task #" + answer.order() + " (unavailable or ambiguous)", answer.text()));
                invalid = true;
            }
        }
        List<Item> items = tasks.items().stream().map(item -> new Item(item.order(), item.title(),
                item.description(), item.order() == 0 ? null : paired.get(item.order()))).toList();
        return new Essay(items, null, true, invalid, unmatched);
    }

    private record SavedAnswer(int order, String text) {}
    private record Tasks(List<Item> items, Map<Integer, Integer> counts, boolean invalid) {}

    private static Tasks tasks(String json) {
        List<Item> items = new ArrayList<>();
        Map<Integer, Integer> counts = new HashMap<>();
        if (json == null || json.isBlank()) return new Tasks(items, counts, false);
        boolean invalid = false;
        try {
            JsonElement root = parse(json);
            if (root == null || !root.isJsonArray()) return new Tasks(items, counts, true);
            for (JsonElement element : root.getAsJsonArray()) {
                String title = text(element);
                String description = null;
                int order = 0;
                if (element.isJsonObject()) {
                    var object = element.getAsJsonObject();
                    order = order(object.get("task_order"));
                    if (order > 0) counts.merge(order, 1, Integer::sum);
                    title = text(object.get("title"));
                    description = text(object.get("description"));
                    JsonElement rawDescription = object.get("description");
                    if (rawDescription != null && !rawDescription.isJsonNull() && description == null) invalid = true;
                }
                if (order == 0) invalid = true;
                if (title == null || title.isBlank()) { invalid = true; continue; }
                items.add(new Item(order, title, description, null));
            }
        } catch (RuntimeException ex) {
            invalid = true;
        }
        if (counts.values().stream().anyMatch(count -> count > 1)) invalid = true;
        // Unkeyed legacy tasks stay visible at the end, without inventing an answer identity.
        items.sort(Comparator.comparingInt(item -> item.order() == 0 ? Integer.MAX_VALUE : item.order()));
        return new Tasks(items, counts, invalid);
    }

    private static String text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    /** Gson objects overwrite duplicate property names, so reject those before reading any identity. */
    private static JsonElement parse(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            checkNames(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Unexpected trailing data");
            return JSON.fromJson(json, JsonElement.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSON", ex);
        }
    }

    private static void checkNames(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IllegalArgumentException("JSON is nested too deeply");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var names = new HashSet<String>();
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) throw new IllegalArgumentException("Duplicate JSON property");
                    checkNames(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) checkNames(reader, depth + 1);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("Invalid JSON value");
        }
    }

    private static int order(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
        String number = value.getAsString();
        if (!number.matches("[1-9][0-9]*")) return 0;
        try { return Integer.parseInt(number); }
        catch (NumberFormatException ex) { return 0; }
    }
}
