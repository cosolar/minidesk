package cn.minims.desk.modules.study.book.vo;

/**
 * 书单条目，progress 为 0-100。
 */
public record BookVO(
        String id,
        String title,
        String author,
        String category,
        Integer totalPages,
        Integer readPages,
        int progress,
        String status,
        Integer rating,
        String startDate,
        String finishDate
) {
    public record NoteVO(String id, String content, Integer pageNo, String createdAt) {
    }
}
