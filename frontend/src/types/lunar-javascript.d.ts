/**
 * `lunar-javascript@1.7.8` 未自带类型定义，这里只补本仓库用到的极小面：
 * 按「月/日」构造农历对象、取中文月日文案、换算出对应公历日期。
 */
declare module 'lunar-javascript' {
  export interface SolarDate {
    getYear(): number
    getMonth(): number
    getDay(): number
  }

  export interface LunarDate {
    /** 中文月份，如「正」「冬」「腊」 */
    getMonthInChinese(): string
    /** 中文日，如「初一」「十九」「廿九」 */
    getDayInChinese(): string
    /** 对应的公历日期 */
    getSolar(): SolarDate
  }

  export const Lunar: {
    fromYmd(year: number, month: number, day: number): LunarDate
  }
}
