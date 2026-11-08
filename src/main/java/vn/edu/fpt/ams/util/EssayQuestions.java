package vn.edu.fpt.ams.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The shared, validated question/requirement model for Essay assignments. */
public final class EssayQuestions {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final Set<String> ANSWER_TYPES = Set.of("TEXT", "DIAGRAM", "BOTH");
    private static final String POINTS_MESSAGE =
            "Điểm phải từ 0.01 đến 100, có tối đa 2 chữ số thập phân.";

    private EssayQuestions() {}

    public record Task(int order, String title, BigDecimal points, List<Requirement> requirements, String rubric) {
        public Task(int order, String title, BigDecimal points, List<Requirement> requirements) {
            this(order, title, points, requirements, "");
        }

        public Task {
            requirements = List.copyOf(requirements);
        }
    }

    public record Requirement(int order, String title, BigDecimal points, String answerType,
                              String rubric, List<String> allowedTools) {
        public Requirement(int order, String title, BigDecimal points, String answerType) {
            this(order, title, points, answerType, "", defaultTools(answerType));
        }

        public Requirement {
            allowedTools = List.copyOf(allowedTools);
        }
    }

    /** Reads saved content without repairing malformed scores, orders, or explicit requirements. */
    public static List<Task> read(String json) {
        try {
            JsonElement root = JSON.fromJson(json, JsonElement.class);

            if (root == null || !root.isJsonArray())
                throw invalid("Dữ liệu câu hỏi tự luận phải là danh sách hợp lệ.");

            return tasks(root.getAsJsonArray(), true);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Dữ liệu câu hỏi tự luận đã lưu không hợp lệ.", ex);
        }
    }

    /** Accepts editor input, assigns sequential orders, and returns the canonical stored shape. */
    public static JsonArray normalize(JsonArray entered, BigDecimal maximum) {
        List<Task> tasks = tasks(entered, false);
        BigDecimal total =
                tasks.stream().map(Task::points).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (maximum != null && total.compareTo(maximum) > 0)
            throw invalid("Tổng điểm các câu hỏi không được vượt quá điểm tối đa của bài tập.");

        return json(tasks, true);
    }

    /** Public projection excludes all grading criteria, including unrecognized private metadata. */
    public static String studentJson(String savedJson) {
        return json(read(savedJson), false).toString();
    }

    private static JsonArray json(List<Task> tasks, boolean teacher) {
        JsonArray result = new JsonArray();

        for (Task task : tasks) {
            JsonObject output = new JsonObject();

            output.addProperty("task_order", task.order());
            output.addProperty("title", task.title());
            output.addProperty("description", "");
            output.addProperty("maximum_points", task.points());
            if (teacher) output.addProperty("rubric", task.rubric());
            JsonArray requirements = new JsonArray();

            for (Requirement requirement : task.requirements()) {
                JsonObject child = new JsonObject();

                child.addProperty("requirement_order", requirement.order());
                child.addProperty("title", requirement.title());
                child.addProperty("maximum_points", requirement.points());
                child.addProperty("answer_type", requirement.answerType());
                if (teacher) child.addProperty("rubric", requirement.rubric());
                JsonArray tools = new JsonArray();
                requirement.allowedTools().forEach(tools::add);
                child.add("allowed_tools", tools);
                requirements.add(child);
            }

            output.add("requirements", requirements);
            result.add(output);
        }

        return result;
    }

    private static List<Task> tasks(JsonArray entered, boolean saved) {
        if (entered == null || entered.isEmpty() || entered.size() > AssignmentRules.MAX_TASKS)
            throw invalid("Thêm từ 1 đến 200 câu hỏi tự luận.");

        List<Task> tasks = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();

        for (int i = 0; i < entered.size(); i++) {
            String prefix = "Câu tự luận " + (i + 1) + ": ";

            try {
                JsonObject source = object(entered.get(i));
                int order = saved ? order(source.get("task_order")) : i + 1;

                if (!orders.add(order)) throw invalid("Số thứ tự câu hỏi không được trùng nhau.");

                String title = requiredText(source, "title", "Nhập nội dung câu hỏi.");
                String description = optionalText(source, "description").strip();

                if (!description.isBlank() && !description.equals(title))
                    title += "\n" + description;

                BigDecimal points = points(source.get("maximum_points"), saved);
                List<Requirement> requirements = requirements(source, points, saved);

                tasks.add(
                        new Task(
                                order,
                                AssignmentRules.normalizeLines(title),
                                points,
                                requirements,
                                rubric(source, 5000)));
            } catch (IllegalArgumentException ex) {
                throw invalid(prefix + ex.getMessage());
            }
        }

        if (saved) tasks.sort(Comparator.comparingInt(Task::order));

        return List.copyOf(tasks);
    }

    private static List<Requirement> requirements(
            JsonObject task, BigDecimal parentPoints, boolean saved) {
        // Legacy scored tasks have one text response covering their existing question points.
        if (!task.has("requirements"))
            return List.of(new Requirement(1, "Trả lời câu hỏi", parentPoints, "TEXT"));

        JsonElement raw = task.get("requirements");

        if (!raw.isJsonArray()
                || raw.getAsJsonArray().isEmpty()
                || raw.getAsJsonArray().size() > AssignmentRules.MAX_REQUIREMENTS)
            throw invalid("Mỗi câu hỏi cần từ 1 đến 10 yêu cầu.");

        List<Requirement> requirements = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < raw.getAsJsonArray().size(); i++) {
            try {
                JsonObject source = object(raw.getAsJsonArray().get(i));
                int order = saved ? order(source.get("requirement_order")) : i + 1;

                if (!orders.add(order)) throw invalid("Số thứ tự yêu cầu không được trùng nhau.");

                String title = requiredText(source, "title", "Nhập nội dung yêu cầu.");
                BigDecimal points = points(source.get("maximum_points"), saved);
                String answerType =
                        requiredText(source, "answer_type", "Chọn hình thức trả lời cho yêu cầu.");

                if (!ANSWER_TYPES.contains(answerType))
                    throw invalid("Hình thức trả lời phải là TEXT, DIAGRAM hoặc BOTH.");

                requirements.add(
                        new Requirement(
                                order, AssignmentRules.normalizeLines(title), points, answerType,
                                rubric(source, 2000), allowedTools(source, answerType)));
                total = total.add(points);
            } catch (IllegalArgumentException ex) {
                throw invalid("Yêu cầu " + (i + 1) + ": " + ex.getMessage());
            }
        }

        if (total.compareTo(parentPoints) > 0)
            throw invalid("Tổng điểm các yêu cầu không được vượt quá điểm của câu hỏi.");

        if (saved) requirements.sort(Comparator.comparingInt(Requirement::order));

        return List.copyOf(requirements);
    }

    private static String rubric(JsonObject source, int maximum) {
        String value = AssignmentRules.normalizeLines(optionalText(source, "rubric")).strip();
        if (AssignmentRules.length(value) > maximum)
            throw invalid("Tiêu chí chấm điểm tối đa " + maximum + " ký tự.");

        return value;
    }

    private static List<String> defaultTools(String answerType) {
        return "TEXT".equals(answerType) ? List.of() : List.of("DRAWIO", "PLANTUML");
    }

    private static List<String> allowedTools(JsonObject source, String answerType) {
        if (!source.has("allowed_tools")) return defaultTools(answerType);
        JsonElement raw = source.get("allowed_tools");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() > 2)
            throw invalid("Chọn công cụ vẽ hợp lệ.");

        Set<String> selected = new java.util.LinkedHashSet<>();
        for (JsonElement entry : raw.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                    || !Set.of("DRAWIO", "PLANTUML").contains(entry.getAsString())
                    || !selected.add(entry.getAsString()))
                throw invalid("Công cụ vẽ phải là draw.io hoặc PlantUML, không trùng nhau.");
        }

        if ("TEXT".equals(answerType) && !selected.isEmpty())
            throw invalid("Yêu cầu viết tự luận không sử dụng công cụ vẽ.");
        if (!"TEXT".equals(answerType) && selected.isEmpty())
            throw invalid("Chọn ít nhất một công cụ vẽ cho yêu cầu sơ đồ.");

        return List.copyOf(selected);
    }

    private static BigDecimal points(JsonElement value, boolean saved) {
        if (value == null
                || !value.isJsonPrimitive()
                || !(value.getAsJsonPrimitive().isNumber()
                        || (!saved && value.getAsJsonPrimitive().isString())))
            throw invalid(POINTS_MESSAGE);

        String text = value.getAsString().strip();

        if (!saved && !text.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) throw invalid(POINTS_MESSAGE);

        try {
            BigDecimal number = new BigDecimal(text);

            if (!AssignmentRules.validPoints(number)) throw invalid(POINTS_MESSAGE);

            return number.setScale(2);
        } catch (NumberFormatException ex) {
            throw invalid(POINTS_MESSAGE);
        }
    }

    private static int order(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid("Số thứ tự phải là số nguyên dương.");

        try {
            int order = value.getAsBigDecimal().intValueExact();

            if (order <= 0) throw invalid("Số thứ tự phải là số nguyên dương.");

            return order;
        } catch (ArithmeticException | NumberFormatException ex) {
            throw invalid("Số thứ tự phải là số nguyên dương.");
        }
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject())
            throw invalid("Nội dung phải là một đối tượng hợp lệ.");

        return value.getAsJsonObject();
    }

    private static String requiredText(JsonObject source, String key, String message) {
        String text = optionalText(source, key).strip();

        if (text.isBlank()) throw invalid(message);

        return text;
    }

    private static String optionalText(JsonObject source, String key) {
        JsonElement value = source.get(key);

        if (value == null || value.isJsonNull()) return "";

        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid("Nội dung văn bản không hợp lệ.");

        return value.getAsString();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
