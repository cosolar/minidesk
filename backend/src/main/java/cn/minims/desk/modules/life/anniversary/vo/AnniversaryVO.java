package cn.minims.desk.modules.life.anniversary.vo;

/**
 * 纪念日与倒数日展示对象，都带「还有几天」。
 *
 * <p>{@code daysLeft} 为负表示已过（仅一次性纪念日会出现），{@code nextDate} 是下一次发生的公历日期。
 */
public record AnniversaryVO(
        String id,
        String title,
        String type,
        String person,
        String date,
        boolean repeatYearly,
        int advanceDays,
        String nextDate,
        long daysLeft,
        boolean lunar,
        String lunarText,
        String note,
        String cover,
        boolean important
) {
    public record CountdownVO(
            String id,
            String title,
            String targetDate,
            String type,
            String color,
            long daysLeft
    ) {
    }
}
