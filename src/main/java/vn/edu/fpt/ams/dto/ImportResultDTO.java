package vn.edu.fpt.ams.dto;

import java.util.List;

/** Kết quả nhập CSV, gồm số lượng và từng dòng để hiển thị ai được thêm, bỏ qua hoặc từ chối. */
public record ImportResultDTO(int imported, int skipped, int rejected, List<ImportLineResult> lines) {
    /** Giữ bản sao danh sách dòng để người gọi không sửa kết quả sau khi nhập xong. */
    public ImportResultDTO {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
