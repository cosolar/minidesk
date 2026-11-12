package cn.minims.desk.modules.life.anniversary.vo;

/**
 * 纪念日与倒数日展示对象，都带「还有几天」。
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
        long daysLeft
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
