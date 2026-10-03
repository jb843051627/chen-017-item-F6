package com.fc.v2.service.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TItemDutyWindowMapper;
import com.fc.v2.model.auto.TItemDutyWindow;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 值守开口钟：局里把出手的时辰照值守切成两段——夜里与法定节假日那两段不作声，
 * 段外即使有到期也只压着等下一轮；岁末那半个月（12-16 至 12-31）另按班表切，
 * 不走寻常那个切法。
 *
 * 班表 t_item_duty_window 一日一行：
 *   day_kind=0 整日静默（法定节假日/调休休息日）
 *   day_kind=1 寻常值守开口（开口时辰照本行，缺省 09:00–17:00）
 *   day_kind=2 岁末半月班表开口（开口时辰照本行，另走岁末幂等规矩）
 * 班表缺行时：岁末半月内按 kind=2 兜底，其余日子按 kind=1 兜底。
 *
 * 时段比较统一走墙钟字符串：JDBC 的 serverTimezone 与本机时区不对称，
 * 直接把 java.util.Date 作参数会整体偏移，边界用例会错档（同 alert-rule 实测教训）。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Component
public class CheckDutyClock {

    /** 岁末半月起（含）：12 月 16 日 */
    public static final int YEAR_END_FROM_MONTH = Calendar.DECEMBER;
    public static final int YEAR_END_FROM_DAY = 16;
    /** 岁末半月止（含）：12 月 31 日 */
    public static final int YEAR_END_TO_MONTH = Calendar.DECEMBER;
    public static final int YEAR_END_TO_DAY = 31;

    /** 班别：整日静默（法定节假日） */
    public static final int KIND_SILENT = 0;
    /** 班别：寻常值守开口 */
    public static final int KIND_NORMAL = 1;
    /** 班别：岁末半月班表开口 */
    public static final int YEAR_END = 2;

    /** 班表缺行时的兜底开口时辰 */
    public static final String DEFAULT_OPEN_FROM = "09:00:00";
    public static final String DEFAULT_OPEN_TO = "17:00:00";

    @Resource
    private TItemDutyWindowMapper dutyWindowMapper;

    /** 当日是否岁末半月（12-16 至 12-31，含两端） */
    public boolean isYearEnd(Date at) {
        if (at == null) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        return c.get(Calendar.MONTH) == Calendar.DECEMBER
                && c.get(Calendar.DAY_OF_MONTH) >= YEAR_END_FROM_DAY;
    }

    /**
     * 该时刻作不作声。
     * 整日静默（法定节假日）→ 否；
     * 时刻落在当班开口时辰段内（含起、不含止）→ 是；
     * 段外即夜里，即使有到期也只压着 → 否。
     */
    public boolean isOpen(Date at) {
        Slot slot = slotAt(at);
        return slot != null && slot.open;
    }

    /** 该时刻当班是否岁末班表（决定同日幂等那笔规矩用不用） */
    public boolean isYearEndShift(Date at) {
        Slot slot = slotAt(at);
        return slot != null && slot.yearEnd;
    }

    /**
     * 止点之后的**头一个开口段**，到此刻是不是已经整个过去了。
     * 止点本身落在段外（夜里/节假日前）不算错过——"段外有到期只压着，等下一轮"，
     * 头一个开口段就是那"下一轮"；只有此刻已不早于头一个开口段的止点，
     * 即确有开口轮次被整段跳过去，才算"日子过了还没派出去"，该撤回。
     */
    public boolean missedFirstOpportunity(Date dueAt, Date at) {
        Date[] first = firstOpportunity(dueAt, at);
        if (first == null || at == null) {
            return false;
        }
        return at.getTime() >= first[1].getTime();
    }

    /**
     * 止点之后头一个开口段 [起, 止)。
     * 从止点当日起逐日找：整日静默跳过；开口段止点不晚于止点时刻的（止点当天已收尾）
     * 也算过，改看下一日；头一个开口余量为正的段即是。
     * 班表一次性取回建索引，避免每条派单逐日查库。
     */
    private Date[] firstOpportunity(Date dueAt, Date at) {
        if (dueAt == null) {
            return null;
        }
        int spanDays = 400;
        if (at != null) {
            long d = (at.getTime() - dueAt.getTime()) / 86400000L + 2;
            if (d > 0 && d < spanDays) {
                spanDays = (int) d;
            }
        }
        java.util.Map<String, TItemDutyWindow> rows = lookupRows(day(dueAt), plusDays(dueAt, spanDays - 1));

        Calendar cur = atDayStart(dueAt);
        for (int i = 0; i < spanDays; i++) {
            Date dayStart = cur.getTime();
            String day = day(dayStart);
            TItemDutyWindow row = rows.get(day);
            int kind;
            String from;
            String to;
            if (row == null) {
                kind = isYearEnd(dayStart) ? YEAR_END : KIND_NORMAL;
                from = DEFAULT_OPEN_FROM;
                to = DEFAULT_OPEN_TO;
            } else {
                kind = row.getDayKind() == null ? KIND_NORMAL : row.getDayKind();
                from = row.getOpenFrom() == null ? DEFAULT_OPEN_FROM : row.getOpenFrom().toString();
                to = row.getOpenTo() == null ? DEFAULT_OPEN_TO : row.getOpenTo().toString();
            }
            if (kind != KIND_SILENT) {
                Date winEnd = atTime(day, to);
                if (winEnd.after(dueAt)) {
                    return new Date[] {atTime(day, from), winEnd};
                }
            }
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    /** 区间内班表一次取回，按自然日建索引 */
    private java.util.Map<String, TItemDutyWindow> lookupRows(String fromDay, String toDay) {
        java.util.Map<String, TItemDutyWindow> map = new java.util.HashMap<String, TItemDutyWindow>();
        if (this.dutyWindowMapper == null) {
            return map;
        }
        List<TItemDutyWindow> list = this.dutyWindowMapper.selectList(new QueryWrapper<TItemDutyWindow>()
                .eq("del_flag", 0)
                .ge("cal_date", fromDay)
                .le("cal_date", toDay));
        for (TItemDutyWindow w : list) {
            if (w.getCalDate() != null) {
                map.put(day(w.getCalDate()), w);
            }
        }
        return map;
    }

    private static String plusDays(Date d, int n) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DAY_OF_MONTH, n);
        return day(c.getTime());
    }

    /** 某日某时分拼成时刻 */
    private static Date atTime(String day, String hhmmss) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(day + " " + hhmmss);
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /** 到当日零点 */
    private static Calendar atDayStart(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /**
     * 解析当班开口段。班表缺行按岁末/寻常兜底；day_kind=0 整日静默。
     * 开口段比较用墙钟 HH:mm:ss 字符串，含起不含止。
     */
    public Slot slotAt(Date at) {
        if (at == null) {
            return null;
        }
        String day = day(at);
        String hhmmss = hhmmss(at);
        TItemDutyWindow row = null;
        if (this.dutyWindowMapper != null) {
            row = this.dutyWindowMapper.selectOne(new QueryWrapper<TItemDutyWindow>()
                    .eq("cal_date", day)
                    .eq("del_flag", 0)
                    .last("limit 1"));
        }
        int kind;
        String from;
        String to;
        if (row == null) {
            kind = isYearEnd(at) ? YEAR_END : KIND_NORMAL;
            from = DEFAULT_OPEN_FROM;
            to = DEFAULT_OPEN_TO;
        } else {
            kind = row.getDayKind() == null ? KIND_NORMAL : row.getDayKind();
            from = row.getOpenFrom() == null ? DEFAULT_OPEN_FROM : row.getOpenFrom().toString();
            to = row.getOpenTo() == null ? DEFAULT_OPEN_TO : row.getOpenTo().toString();
        }
        if (kind == KIND_SILENT) {
            return new Slot(false, kind == YEAR_END, kind, day, from, to);
        }
        boolean open = hhmmss.compareTo(from) >= 0 && hhmmss.compareTo(to) < 0;
        return new Slot(open, kind == YEAR_END, kind, day, from, to);
    }

    /** 墙钟 yyyy-MM-dd */
    public static String day(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    /** 墙钟 HH:mm:ss */
    public static String hhmmss(Date d) {
        return new SimpleDateFormat("HH:mm:ss").format(d);
    }

    /** 墙钟 yyyy-MM-dd HH:mm:ss（同一把尺：翻单与回写共用） */
    public static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 当班开口段情形。
     */
    public static class Slot {
        /** 此刻作不作声 */
        private final boolean open;
        /** 是否岁末班表 */
        private final boolean yearEnd;
        /** 班别 0静默 1寻常 2岁末 */
        private final int kind;
        /** 所属自然日 yyyy-MM-dd */
        private final String day;
        /** 开口起 */
        private final String from;
        /** 开口止 */
        private final String to;

        public Slot(boolean open, boolean yearEnd, int kind, String day, String from, String to) {
            this.open = open;
            this.yearEnd = yearEnd;
            this.kind = kind;
            this.day = day;
            this.from = from;
            this.to = to;
        }

        public boolean isOpen() {
            return open;
        }

        public boolean isYearEnd() {
            return yearEnd;
        }

        public int getKind() {
            return kind;
        }

        public String getDay() {
            return day;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }
    }
}
