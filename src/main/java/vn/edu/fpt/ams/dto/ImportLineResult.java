package vn.edu.fpt.ams.dto;

/** Outcome of one nonblank CSV data row, retaining its original file line number. */
public record ImportLineResult(int lineNumber, String email, Outcome outcome, String reason) {
    public enum Outcome {
        IMPORTED, SKIPPED, REJECTED;

        /** Resolve the display label in compiled Java instead of relying on JSP source syntax. */
        public String label() {
            return switch (this) {
                case IMPORTED -> "Thêm thành công";
                case SKIPPED -> "Bỏ qua";
                case REJECTED -> "Không hợp lệ";
            };
        }
    }
}
