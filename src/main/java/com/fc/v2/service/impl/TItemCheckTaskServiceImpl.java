package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemCheckTaskMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemCheckTask;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.model.custom.CheckRoundBook;
import com.fc.v2.service.ITItemCheckTaskService;

/**
 * 要素定期核查派单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * <p>判断只凭一把尺：该动手那一日到了没有（{@link #due(Date, Date)}，含恰好到期）。
 * 翻单子（listDue）与回写（runOnce）共用这一把，两处不各用各的。
 * 该动手那一日不许往后推来凑数——全类没有任何一处回写 due_at。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemCheckTaskServiceImpl implements ITItemCheckTaskService {

    /** 条目情形：0候派 1已派出 2派不成 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    /** 夜里不作声段 [02:00, 05:00)：段外即使有到期也只压着，等下一轮 */
    private static final String NIGHT_FROM = "02:00:00";
    private static final String NIGHT_TO = "05:00:00";

    /** 法定节假日（固定日期部分，MM-dd）：元旦、劳动节、国庆三日 */
    private static final Set<String> HOLIDAYS = new HashSet<String>(
            java.util.Arrays.asList("01-01", "05-01", "10-01", "10-02", "10-03"));

    /** 岁末班表段 [12-16, 12-31]：另按班表切，不走头一句那个切法 */
    private static final String YEAR_END_FROM = "12-16";
    private static final String YEAR_END_TO = "12-31";

    @javax.annotation.Resource
    private TItemCheckTaskMapper itemCheckTaskMapper;

    @javax.annotation.Resource
    private TItemDeptArchMapper itemDeptArchMapper;

    @javax.annotation.Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;

    @Override
    public TItemCheckTask selectTItemCheckTaskById(Long id) {
        return this.itemCheckTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 唯一的一把尺：该动手那一日到了没有。due_at 存的是当日的止点时刻，
     * 止点到了（含恰好到期）即归进本轮；没到的不动。
     */
    private static boolean due(Date dueAt, Date at) {
        return dueAt != null && ts(dueAt).compareTo(ts(at)) <= 0;
    }

    /** 岁末那半个月（12-16 至 12-31，按墙钟日历） */
    private static boolean isYearEnd(Date at) {
        String md = ts(at).substring(5, 10);
        return md.compareTo(YEAR_END_FROM) >= 0 && md.compareTo(YEAR_END_TO) <= 0;
    }

    /**
     * 头一句的切法：夜里与法定节假日那两段时间不作声。
     * 岁末那半个月另按班表切，不走这一句——班表段里不作声的规矩不压。
     */
    private static boolean silent(Date at) {
        if (isYearEnd(at)) {
            return false;
        }
        String s = ts(at);
        if (HOLIDAYS.contains(s.substring(5, 10))) {
            return true;
        }
        String hm = s.substring(11);
        return hm.compareTo(NIGHT_FROM) >= 0 && hm.compareTo(NIGHT_TO) < 0;
    }

    /**
     * 到期且尚未处理的条目（未去重）。段外即使有到期也只压着——交空集，等下一轮。
     * 本轮派出去过的（情形已非候派）在这里就被挡下，下一轮不会再被当作候派挑出来。
     */
    private List<TItemCheckTask> dueWaiting(Date at) {
        List<TItemCheckTask> out = new ArrayList<TItemCheckTask>();
        if (at == null || silent(at)) {
            return out;
        }
        List<TItemCheckTask> all = this.itemCheckTaskMapper.selectList(new QueryWrapper<TItemCheckTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .orderByAsc("id"));
        for (TItemCheckTask r : all) {
            if (due(r.getDueAt(), at)) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * 岁末班表段里同一事项一轮只认头一次落地：先落下的（id 小的）入账，
     * 当日第二回来的不入账、原样留着仍候派。段外不去重。
     */
    private static List<TItemCheckTask> roundPick(List<TItemCheckTask> rows, Date at) {
        if (!isYearEnd(at)) {
            return rows;
        }
        Set<String> seen = new HashSet<String>();
        List<TItemCheckTask> out = new ArrayList<TItemCheckTask>();
        for (TItemCheckTask r : rows) {
            String key = r.getItemNo();
            if (key != null && !key.isEmpty() && !seen.add(key)) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    @Override
    public List<TItemCheckTask> listDue(Date at) {
        return roundPick(dueWaiting(at), at);
    }

    @Override
    public int runOnce(Date at) {
        return runOnce(at, null);
    }

    @Override
    public int runOnce(Date at, CheckRoundBook book) {
        CheckRoundBook b = book != null ? book : new CheckRoundBook();
        if (at == null) {
            b.setWindowOpen(false);
            return 0;
        }
        List<TItemCheckTask> waiting = dueWaiting(at);
        List<TItemCheckTask> pick = roundPick(waiting, at);
        b.setWindowOpen(!silent(at));
        b.setPicked(pick.size());
        b.setHeld(waiting.size() - pick.size());
        int ok = 0;
        int fail = 0;
        // 撤并底册整轮只查一回；开单前逐条对照，绊一下不趴下
        List<TItemDeptArch> dissolved = pick.isEmpty()
                ? new ArrayList<TItemDeptArch>()
                : this.itemDeptArchMapper.selectList(new QueryWrapper<TItemDeptArch>()
                        .eq("del_flag", 0).eq("status", 1));
        for (TItemCheckTask r : pick) {
            String reason = refuseReason(r, dissolved);
            if (reason != null) {
                fail += mark(r.getId(), STATUS_FAIL, reason, at);
            } else {
                ok += mark(r.getId(), STATUS_DONE, null, at);
            }
        }
        b.setDispatched(ok);
        b.setFailed(fail);
        return ok;
    }

    /**
     * 开单前的拒派缘故：承办那一栏整季空着，或这一册部门已经撤并。
     * 查不出实据的不拦（底册没挂上的部门不耽误派单）。
     */
    private static String refuseReason(TItemCheckTask r, List<TItemDeptArch> dissolved) {
        String content = r.getContent();
        if (content == null || content.trim().isEmpty()) {
            return "派不成：承办栏整季空着";
        }
        for (TItemDeptArch arch : dissolved) {
            String no = arch.getSiteNo();
            String name = arch.getSiteName();
            if ((no != null && !no.isEmpty() && content.contains(no))
                    || (name != null && !name.isEmpty() && content.contains(name))) {
                return "派不成：部门已撤并(" + (no != null ? no : name) + ")";
            }
        }
        return null;
    }

    /**
     * 落一笔：情形改过去、这一刻钉住，两件事在同一条 UPDATE 里办完，少一件都不算成。
     * 只动候派行（并发或重复跑时守卫：本轮派出去过的不会再被改写）。
     * 时刻按墙钟字符串绑定，与查询那把尺同源；due_at 不在此处出现，头回定的日子不许盖。
     */
    private int mark(Long id, int status, String reason, Date at) {
        UpdateWrapper<TItemCheckTask> uw = new UpdateWrapper<TItemCheckTask>()
                .eq("id", id)
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0)
                .set("status", status)
                .set("update_time", ts(at));
        if (reason != null) {
            uw.set("remark", reason);
        }
        return this.itemCheckTaskMapper.update(null, uw);
    }

    @Override
    public int withdrawOverdue(Date at) {
        if (at == null) {
            return 0;
        }
        // 撤回是行政扫尾，不是出手派单，不受出手段约束；到期判定仍是同一把尺
        List<TItemCheckTask> all = this.itemCheckTaskMapper.selectList(new QueryWrapper<TItemCheckTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .orderByAsc("id"));
        int n = 0;
        for (TItemCheckTask r : all) {
            if (due(r.getDueAt(), at)) {
                n += mark(r.getId(), STATUS_FAIL, "派不成：日子过了未派出，撤回", at);
            }
        }
        return n;
    }

    @Override
    public int generateFromCatalog(Date dueDay) {
        if (dueDay == null) {
            return 0;
        }
        Date dueAt = endOfDay(dueDay);
        List<TItemCatalogRow> rows = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .orderByAsc("id"));
        int ok = 0;
        for (TItemCatalogRow row : rows) {
            String itemNo = catalogItemNo(row);
            Integer dup = this.itemCheckTaskMapper.selectCount(new QueryWrapper<TItemCheckTask>()
                    .eq("item_no", itemNo));
            if (dup != null && dup > 0) {
                continue;
            }
            TItemCheckTask t = new TItemCheckTask();
            t.setItemNo(itemNo);
            t.setDueAt(dueAt);
            t.setAmount(BigDecimal.ZERO);
            t.setContent("事项码：" + (row.getItemCode() != null ? row.getItemCode() : "")
                    + "；册次：" + (row.getBatchNo() != null ? row.getBatchNo() : "")
                    + " 第" + (row.getRowNo() != null ? row.getRowNo() : row.getId()) + "行"
                    + "；事由：要素定期核查");
            t.setStatus(STATUS_WAIT);
            t.setDelFlag(0);
            if (this.itemCheckTaskMapper.insert(t) > 0) {
                ok++;
                // 条目挂接：本轮出过的不再重出
                this.itemCatalogRowMapper.update(null, new UpdateWrapper<TItemCatalogRow>()
                        .eq("id", row.getId())
                        .eq("status", 0)
                        .set("status", 1));
            }
        }
        return ok;
    }

    /** 派单号从条目直出：册次+行次，不另请一人抄第二遍 */
    private static String catalogItemNo(TItemCatalogRow row) {
        String batch = row.getBatchNo() != null ? row.getBatchNo() : "";
        Object seq = row.getRowNo() != null ? row.getRowNo() : row.getId();
        return "HC" + batch + "-" + seq;
    }

    @Override
    public Long redispatch(Long oldId, Date newDueDay) {
        if (oldId == null || newDueDay == null) {
            return null;
        }
        TItemCheckTask old = this.itemCheckTaskMapper.selectById(oldId);
        if (old == null || old.getDelFlag() == null || old.getDelFlag() != 0
                || old.getStatus() == null || old.getStatus() != STATUS_FAIL) {
            return null;
        }
        String base = old.getItemNo() != null && !old.getItemNo().isEmpty()
                ? old.getItemNo() : "OLD" + old.getId();
        Integer used = this.itemCheckTaskMapper.selectCount(new QueryWrapper<TItemCheckTask>()
                .likeRight("item_no", base + "-R"));
        int seq = (used == null ? 0 : used) + 1;
        String itemNo = base + "-R" + seq;
        while (this.itemCheckTaskMapper.selectCount(new QueryWrapper<TItemCheckTask>()
                .eq("item_no", itemNo)) > 0) {
            seq++;
            itemNo = base + "-R" + seq;
        }
        TItemCheckTask t = new TItemCheckTask();
        t.setItemNo(itemNo);
        t.setDueAt(endOfDay(newDueDay));
        t.setAmount(old.getAmount());
        t.setContent(old.getContent());
        t.setStatus(STATUS_WAIT);
        t.setDelFlag(0);
        t.setRemark("撤回重派：原单 " + base + " 留底");
        if (this.itemCheckTaskMapper.insert(t) > 0) {
            return t.getId();
        }
        return null;
    }

    /** 该动手那一日算到当日的止点（23:59:59） */
    private static Date endOfDay(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
