package com.nest.utils;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 租房周期工具：周期（YYYY-MM）的推进、起止日计算。
 */
public final class RentPeriodUtil {

    private RentPeriodUtil() {}

    /** 当前周期，如 2026-09 */
    public static String currentPeriod() {
        return YearMonth.now().toString();
    }

    /** 周期推进：delta 为负表示回退，如 advance("2026-10", 1) = "2026-11" */
    public static String advance(String period, int delta) {
        return YearMonth.parse(period).plusMonths(delta).toString();
    }

    /** 周期起始日（当月 1 号） */
    public static LocalDate startOf(String period) {
        return YearMonth.parse(period).atDay(1);
    }

    /** 周期结束日（当月最后一天） */
    public static LocalDate endOf(String period) {
        return YearMonth.parse(period).atEndOfMonth();
    }
}
