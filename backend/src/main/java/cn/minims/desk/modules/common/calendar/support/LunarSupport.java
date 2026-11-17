package cn.minims.desk.modules.common.calendar.support;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.LunarMonth;
import com.nlf.calendar.Solar;
import java.time.LocalDate;

/**
 * 农历换算工具：判断农历纪念日、生成农历文案、计算下一次农历发生日。
 */
public final class LunarSupport {

    /** anniversary.lunar 列取该值表示按月/日视为农历 */
    private static final int LUNAR_FLAG = 1;

    /** 农历年最多向后找 3 年（含当年），覆盖闰月缺失导致的跳年 */
    private static final int MAX_YEAR_OFFSET = 2;

    private LunarSupport() {
    }

    /**
     * 判断纪念日是否按农历滚动。
     *
     * @param lunar anniversary.lunar 列
     * @return true 表示农历
     */
    public static boolean isLunar(Integer lunar) {
        return lunar != null && lunar == LUNAR_FLAG;
    }

    /**
     * 公历日期对应的农历文案，如「农历冬月初五」。
     *
     * @param solarDate 公历日期
     * @return 农历文案；入参为 null 时返回 null
     */
    public static String lunarText(LocalDate solarDate) {
        if (solarDate == null) {
            return null;
        }
        Lunar lunar = Solar.fromYmd(solarDate.getYear(), solarDate.getMonthValue(), solarDate.getDayOfMonth())
                .getLunar();
        return "农历" + lunar.getMonthInChinese() + "月" + lunar.getDayInChinese();
    }

    /**
     * 计算农历纪念日的下一次发生日（不早于 today）。
     *
     * @param storedSolarDate 入库的原始公历日期，其农历月/日才是纪念口径
     * @param today           基准日
     * @return 下一次发生日；闰月与农历均不可用时退回原公历月日滚动
     */
    public static LocalDate nextLunarOccurrence(LocalDate storedSolarDate, LocalDate today) {
        if (storedSolarDate == null) {
            return null;
        }
        LocalDate base = today == null ? LocalDate.now() : today;
        Lunar stored;
        try {
            stored = Solar.fromYmd(storedSolarDate.getYear(), storedSolarDate.getMonthValue(),
                    storedSolarDate.getDayOfMonth()).getLunar();
        } catch (RuntimeException ex) {
            // 库里存的公历日期理论上一定合法，异常时退回公历滚动，避免整条记录不可用
            return nextSolarOccurrence(storedSolarDate, base);
        }
        int month = stored.getMonth();
        int day = stored.getDay();
        int startYear = Solar.fromYmd(base.getYear(), base.getMonthValue(), base.getDayOfMonth())
                .getLunar().getYear();
        for (int offset = 0; offset <= MAX_YEAR_OFFSET; offset++) {
            int lunarYear = startYear + offset;
            LocalDate candidate = tryLunarDate(lunarYear, month, day);
            // 闰月并非每年都有；缺失时回退到同名的非闰月，否则农历纪念日会整年消失
            if (candidate == null && month < 0) {
                candidate = tryLunarDate(lunarYear, -month, day);
            }
            if (candidate != null && !candidate.isBefore(base)) {
                return candidate;
            }
        }
        return nextSolarOccurrence(storedSolarDate, base);
    }

    /**
     * 农历年月日转公历；该农历年没有这个月时返回 null。
     * 农历三十遇小月（只有 29 天）时按当月最后一天「廿九」落地，
     * 否则生日落在三十的人会整年消失，这比提前一天更符合预期。
     */
    private static LocalDate tryLunarDate(int lunarYear, int month, int day) {
        try {
            int maxDay = LunarMonth.fromYm(lunarYear, month).getDayCount();
            int actualDay = Math.min(day, maxDay);
            Solar solar = Lunar.fromYmd(lunarYear, month, actualDay).getSolar();
            return LocalDate.of(solar.getYear(), solar.getMonth(), solar.getDay());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 兜底：按公历月日滚动到今年或明年 */
    private static LocalDate nextSolarOccurrence(LocalDate storedSolarDate, LocalDate today) {
        LocalDate candidate = storedSolarDate.withYear(today.getYear());
        if (candidate.isBefore(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }
}
