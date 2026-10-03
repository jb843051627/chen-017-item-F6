package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
import com.fc.v2.model.custom.CheckRoundResult;
import com.fc.v2.service.ITItemCheckTaskService;
import com.fc.v2.service.support.CheckDutyClock;

import javax.annotation.Resource;

/**
 * 要素定期核查派单 Service业务层处理（scheduling-job 形状：照日子一轮一轮跑）。
 *
 * 一把尺两头用：翻单（listDue/runRound 挑单）与回写后复核都是
 * "候派(status=0) 且 止点 due_at &lt;= 此刻"，墙钟字符串比较，两处绝不用两把尺。
 *
 * 出手时辰三句：
 * 一、夜里与法定节假日两段不作声，段外有到期也只压着等下一轮；
 * 二、岁末半月（12-16..12-31）另按班表切；那半个月同一事项一轮只认当日头一次落地，
 *    当日第二回来的不入账，先落下的原样留着；
 * 三、本轮派出去过的下一轮不再当候派挑出；派成=情形改已派出+钉住派出时刻，两笔齐全。
 *
 * 单条走不让整轮趴下：撤并/承办栏空着/过期未派，就地转派不成、缘故随行，屏上翻得着。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemCheckTaskServiceImpl implements ITItemCheckTaskService {

    /** 条目情形：候派 */
    private static final int STATUS_WAIT = 0;
    /** 条目情形：已派出 */
    private static final int STATUS_DONE = 1;
    /** 条目情形：派不成 */
    private static final int STATUS_FAIL = 2;

    /** 事由归类三桩 */
    private static final int REASON_ELEMENT = 1;
    private static final int REASON_LIMIT = 2;
    private static final int REASON_HOOK = 3;

    /** 提前几日开口的容许范围（全单唯一可挪的一栏，不许借它把止点后推） */
    private static final BigDecimal AMOUNT_MIN = BigDecimal.ZERO;
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("365");

    /** 单轮至多处置条数（防一轮拖死，余下下轮再走） */
    private static final int BATCH_MAX = 500;

    /** 轮次序号：同秒内多回执行也各有各的轮次号，不串账 */
    private static final java.util.concurrent.atomic.AtomicLong ROUND_SEQ =
            new java.util.concurrent.atomic.AtomicLong(0);

    @Resource
    private TItemCheckTaskMapper itemCheckTaskMapper;
    @Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;
    @Resource
    private TItemDeptArchMapper itemDeptArchMapper;
    @Resource
    private CheckDutyClock dutyClock;

    @Override
    public TItemCheckTask selectTItemCheckTaskById(Long id) {
        return this.itemCheckTaskMapper.selectById(id);
    }

    @Override
    public List<TItemCheckTask> selectTItemCheckTaskList(
            com.baomidou.mybatisplus.core.conditions.Wrapper<TItemCheckTask> queryWrapper) {
        return this.itemCheckTaskMapper.selectList(queryWrapper);
    }

    /**
     * 同一把尺翻单：只看候派 + 止点到没到（due_at &lt;= at，含当日止点），
     * 不掺值守时段——"到没到"与"作不作声"是两把不同的规矩。
     */
    @Override
    public List<TItemCheckTask> listDue(Date at) {
        if (at == null) {
            return new ArrayList<TItemCheckTask>();
        }
        List<TItemCheckTask> all = this.itemCheckTaskMapper.selectList(new QueryWrapper<TItemCheckTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TItemCheckTask> due = new ArrayList<TItemCheckTask>();
        for (TItemCheckTask r : all) {
            if (r != null && dueBy(r, at)) {
                due.add(r);
            }
        }
        Collections.sort(due, DUE_THEN_ID_ASC);
        return due;
    }

    /** 旧跑法：入参与返回照旧；委托新跑法，回"已派出"那一笔 */
    @Override
    public int runOnce(Date at) {
        if (at == null) {
            return 0;
        }
        return doRunRound(at).getDispatchedCount();
    }

    // ------------------------------------------------------------------
    // 新跑法一：名录照口径一键开单
    // ------------------------------------------------------------------

    @Override
    public List<TItemCheckTask> generateFromCatalog() {
        List<TItemCatalogRow> rows = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("del_flag", 0));
        List<TItemCheckTask> opened = new ArrayList<TItemCheckTask>();
        String seqDay = dayStamp(new Date());
        int[] seq = new int[] {0};
        for (TItemCatalogRow row : rows) {
            try {
                // 挂起（status=2）的名录行不开单；待核/已挂接照常一轮轮核
                if (row != null && row.getStatus() != null && row.getStatus() == 2) {
                    continue;
                }
                TItemCheckTask t = openOneFromRow(row, seqDay, seq);
                if (t != null) {
                    opened.add(t);
                }
            } catch (RuntimeException ex) {
                // 单行开不成不挡后面行（名录里该行要素不齐等），跳过继续
            }
        }
        return opened;
    }

    private TItemCheckTask openOneFromRow(TItemCatalogRow row, String seqDay, int[] seq) {
        if (row == null || isBlank(row.getItemCode()) || row.getDueAt() == null
                || row.getReasonType() == null
                || row.getReasonType() < REASON_ELEMENT || row.getReasonType() > REASON_HOOK) {
            return null;
        }
        BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
        if (amount.compareTo(AMOUNT_MIN) < 0 || amount.compareTo(AMOUNT_MAX) > 0) {
            return null;
        }
        String siteNo = isBlank(row.getSiteNo()) ? null : row.getSiteNo().trim();

        // 同一桩已开过单（候派/已派出/派不成皆然）不另开，避免一轮一轮重复派
        Integer exist = this.itemCheckTaskMapper.selectCount(new QueryWrapper<TItemCheckTask>()
                .eq("del_flag", 0)
                .eq("item_code", row.getItemCode().trim())
                .eq("reason_type", row.getReasonType())
                .eq("due_at", CheckDutyClock.ts(row.getDueAt()))
                .eq(siteNo != null, "site_no", siteNo)
                .isNull(siteNo == null, "site_no")
                .last("limit 1"));
        if (exist != null && exist > 0) {
            return null;
        }

        TItemCheckTask t = new TItemCheckTask();
        t.setDelFlag(0);
        t.setItemCode(row.getItemCode().trim());
        t.setReasonType(row.getReasonType());
        t.setAmount(amount);
        t.setDueAt(copy(row.getDueAt()));
        t.setOpenAt(minusDays(row.getDueAt(), amount));
        t.setSiteNo(siteNo);

        TItemDeptArch arch = siteNo == null ? null : findArch(siteNo);
        String reason;
        if (siteNo == null) {
            // 承办栏整季空着：照直开一张派不成的单，屏上翻得着、后面的照走
            t.setStatus(STATUS_FAIL);
            reason = "承办部门栏整季空着，无人可派";
            t.setFailReason(reason);
        } else if (arch == null || (arch.getStatus() != null && arch.getStatus() == 1)) {
            // 这一册部门已经撤并：就地转派不成，缘故落在这行上
            t.setSiteName(arch == null ? null : arch.getSiteName());
            t.setStatus(STATUS_FAIL);
            reason = arch == null ? "承办部门底册查无此册" : "承办部门已撤并，无人可派";
            t.setFailReason(reason);
        } else {
            t.setSiteName(arch.getSiteName());
            t.setStatus(STATUS_WAIT);
            reason = null;
        }
        t.setContent(buildContent(t, reason));
        t.setItemNo(nextItemNo(seqDay, seq));
        t.setCreateBy("Task");
        t.setCreateTime(new Date());
        this.itemCheckTaskMapper.insert(t);
        return t;
    }

    // ------------------------------------------------------------------
    // 新跑法二：照时刻跑一轮，算定再落库，三笔同回点算
    // ------------------------------------------------------------------

    @Override
    public CheckRoundResult runRound(Date at) {
        if (at == null) {
            throw new IllegalArgumentException("执行时刻不能为空（跑不起来与本轮无事不是一回事）");
        }
        return doRunRound(at);
    }

    private CheckRoundResult doRunRound(Date at) {
        CheckRoundResult result = new CheckRoundResult();
        result.setRunAt(copy(at));

        CheckDutyClock.Slot slot = this.dutyClock.slotAt(at);
        boolean open = slot != null && slot.isOpen();
        boolean yearEndShift = slot != null && slot.isYearEnd();
        result.setWindowOpen(open);

        // 同一把尺翻单（回写后复核仍用它）
        List<TItemCheckTask> picked = listDue(at);
        if (picked.size() > BATCH_MAX) {
            picked = new ArrayList<TItemCheckTask>(picked.subList(0, BATCH_MAX));
        }
        result.setPicked(picked.size());

        // 轮次号到秒 + 进程内序号，同秒多回执行也不串账
        String roundNo = "R" + new SimpleDateFormat("yyyyMMddHHmmss").format(at)
                + "-" + String.format("%04d", ROUND_SEQ.incrementAndGet() % 10000);
        result.setRoundNo(roundNo);

        // 第一句：段外（夜里/法定节假日）不作声，到期也只压着，等下一轮
        if (!open) {
            result.setHeld(picked.size());
            result.setWaitingCount(picked.size());
            result.setMessage("非值守开口时段，到期" + picked.size() + "条压着等下一轮");
            return result;
        }

        // 岁末半月：同事项当日已落地（含本回早些时候另一回执行）的标记
        Set<String> landedToday = new HashSet<String>();
        if (yearEndShift) {
            landedToday.addAll(loadLandedKeysToday(at));
        }

        int duplicateHeld = 0;
        for (TItemCheckTask pickedOne : picked) {
            try {
                String outcome = processOne(pickedOne.getId(), at, roundNo, yearEndShift, landedToday);
                if ("held".equals(outcome)) {
                    duplicateHeld++;
                }
            } catch (RuntimeException ex) {
                // 单条走不通不能让整轮趴下：跳过这一条，后面的照次序往下走
            }
        }
        result.setDuplicateHeld(duplicateHeld);

        // 三笔由同一回点算交出来：照本轮回写后的档逐条点，不另起一把尺
        int[] tally = tallyRound(picked, roundNo);
        result.setDispatchedCount(tally[0]);
        result.setFailedCount(tally[1]);
        result.setWaitingCount(tally[2]);
        if (picked.isEmpty()) {
            // 本轮确实没挑出该动的——据实写"本轮无事"，与"跑不起来"不是一回事
            result.setMessage("本轮无事");
        } else {
            result.setMessage("本轮已派出" + tally[0] + "条、派不成" + tally[1]
                    + "条、候派" + tally[2] + "条");
        }
        return result;
    }

    /**
     * 处置一条。返回 dispatched/failed/held。
     * 回写前重查：只认档里此刻仍是候派且止点已到的，防并发/重复执行。
     */
    private String processOne(Long id, Date at, String roundNo, boolean yearEndShift,
                              Set<String> landedToday) {
        TItemCheckTask r = this.itemCheckTaskMapper.selectById(id);
        if (r == null || !isWaiting(r) || !dueBy(r, at)) {
            // 本轮派出去过的，下一轮（或并发的另一回）不许再当候派挑出来
            return "skipped";
        }

        // 止点之后头一个开口段已整个过去（节假日/夜里压着到下一轮的不算在内）才撤回，
        // 转派不成；头回止点不动，无派出时刻即保持空着
        if (this.dutyClock.missedFirstOpportunity(r.getDueAt(), at)) {
            markFail(r, "止点后已错过开口轮次仍未派出，撤回转派不成", roundNo);
            return "failed";
        }

        // 开单前再守一次：撤并 / 承办栏整季空着（生成单后才撤并的也在这拦下）。
        // 这道守在岁末去重之前——头一条派不成，不许连累同事项的第二条也被压着。
        if (isBlank(r.getSiteNo())) {
            markFail(r, "承办部门栏整季空着，无人可派", roundNo);
            return "failed";
        }
        TItemDeptArch arch = findArch(r.getSiteNo().trim());
        if (arch == null) {
            markFail(r, "承办部门底册查无此册", roundNo);
            return "failed";
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            markFail(r, "承办部门已撤并，无人可派", roundNo);
            return "failed";
        }

        // 岁末半月：同一事项一轮只认当日头一次落地，当日第二回来的不入账。
        // 已在档里头一次落过（本回早些时候另一回执行）的，键从档里带进来。
        if (yearEndShift) {
            String key = dedupKey(r);
            if (landedToday.contains(key)) {
                return "held";
            }
            markDone(r, at, roundNo);
            // 派成有两件事：情形改已派出、派出时刻钉住——回查两笔齐全才算成，
            // 成了才把去重键记上，先落下的那条原样留着
            TItemCheckTask back = this.itemCheckTaskMapper.selectById(id);
            if (back != null && back.getStatus() != null && back.getStatus() == STATUS_DONE
                    && back.getDispatchAt() != null && roundNo.equals(back.getRoundNo())) {
                landedToday.add(key);
                return "dispatched";
            }
            // 两笔缺一：回补派不成，缘故随行（此条仍不挡整轮）
            markFail(r, "派出回写不齐（情形或派出时刻缺一笔），转派不成", roundNo);
            return "failed";
        }

        markDone(r, at, roundNo);
        // 派成有两件事：情形改已派出、派出时刻钉住——回查两笔齐全才算成
        TItemCheckTask back = this.itemCheckTaskMapper.selectById(id);
        if (back != null && back.getStatus() != null && back.getStatus() == STATUS_DONE
                && back.getDispatchAt() != null && roundNo.equals(back.getRoundNo())) {
            return "dispatched";
        }
        // 两笔缺一：回补派不成，缘故随行（此条仍不挡整轮）
        markFail(r, "派出回写不齐（情形或派出时刻缺一笔），转派不成", roundNo);
        return "failed";
    }

    /**
     * 本回三笔点算：与处置用的是同一批 picked、同一把尺、同一回执行。
     *   已派出：本回 round_no 且 status=1 且 dispatch_at 不空；
     *   派不成：本回 round_no 且 status=2（撤并/空承办/过期撤回，缘故随行）；
     *   候　派：挑出却仍 status=0（岁末同日重复不入账）。
     */
    private int[] tallyRound(List<TItemCheckTask> picked, String roundNo) {
        int dispatched = 0;
        int failed = 0;
        int waiting = 0;
        for (TItemCheckTask p : picked) {
            TItemCheckTask cur = this.itemCheckTaskMapper.selectById(p.getId());
            if (cur == null) {
                continue;
            }
            Integer st = cur.getStatus();
            if (st != null && st == STATUS_DONE && roundNo.equals(cur.getRoundNo())
                    && cur.getDispatchAt() != null) {
                dispatched++;
            } else if (st != null && st == STATUS_FAIL && roundNo.equals(cur.getRoundNo())) {
                failed++;
            } else if (isWaiting(cur)) {
                waiting++;
            }
        }
        return new int[] {dispatched, failed, waiting};
    }

    // ------------------------------------------------------------------
    // 新跑法三：撤回重派——旧单留底，新单从原单直出、另起一号
    // ------------------------------------------------------------------

    @Override
    public TItemCheckTask reissue(Long sourceId) {
        if (sourceId == null) {
            return null;
        }
        TItemCheckTask old = this.itemCheckTaskMapper.selectById(sourceId);
        if (old == null || (old.getDelFlag() != null && old.getDelFlag() == 1)) {
            return null;
        }
        // 已经派出的两笔（情形+派出时刻）齐全，钉住的事不许撤回重派
        if (old.getStatus() != null && old.getStatus() == STATUS_DONE && old.getDispatchAt() != null) {
            return null;
        }

        int[] seq = new int[] {0};
        String newNo = nextItemNo(dayStamp(new Date()), seq);

        // 旧单就地撤回、转派不成、缘故随行；只写这几列——
        // 头回定的日子(due_at)与真正派出的时刻(dispatch_at)原样钉着，不许被后来的盖掉。
        UpdateWrapper<TItemCheckTask> oldUw = new UpdateWrapper<TItemCheckTask>()
                .eq("id", old.getId())
                .eq("del_flag", 0)
                .set("status", STATUS_FAIL)
                .set("fail_reason", "撤回重派，新单号 " + newNo)
                .set("update_by", "Task")
                .set("update_time", new Date());
        this.itemCheckTaskMapper.update(null, oldUw);

        // 新单从原单直出：事项码、承办部门、事由、办法照带；能挪的只有提前几日一栏。
        TItemCheckTask nw = new TItemCheckTask();
        nw.setDelFlag(0);
        nw.setItemNo(newNo);
        nw.setSourceNo(old.getItemNo());
        nw.setItemCode(old.getItemCode());
        nw.setReasonType(old.getReasonType());
        nw.setSiteNo(old.getSiteNo());
        nw.setSiteName(old.getSiteName());
        nw.setContent(old.getContent());
        nw.setAmount(old.getAmount() == null ? BigDecimal.ZERO : old.getAmount());
        nw.setStatus(STATUS_WAIT);

        // 新单从原单直出：该动手那一日照原单钉死，不许往后推来凑数；能挪的只有提前几日一栏。
        Date dueAt = copy(old.getDueAt());
        nw.setDueAt(dueAt);
        nw.setOpenAt(minusDays(dueAt, nw.getAmount()));
        nw.setCreateBy("Task");
        nw.setCreateTime(new Date());
        this.itemCheckTaskMapper.insert(nw);
        return nw;
    }

    // ------------------------------------------------------------------
    // 回写
    // ------------------------------------------------------------------

    /** 派成两笔：情形改已派出 + 钉住派出那一刻 + 本轮轮次；不碰 due_at。 */
    private void markDone(TItemCheckTask r, Date at, String roundNo) {
        // 内存对象先落上两笔（回查即档内状态）
        r.setStatus(STATUS_DONE);
        r.setDispatchAt(copy(at));
        r.setRoundNo(roundNo);
        // 条件 UPDATE 只认此刻仍候派的行：并发/重复执行时抢不到即不算成
        UpdateWrapper<TItemCheckTask> uw = new UpdateWrapper<TItemCheckTask>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .set("status", STATUS_DONE)
                .set("dispatch_at", CheckDutyClock.ts(at))
                .set("round_no", roundNo)
                .set("update_by", "Task")
                .set("update_time", new Date());
        int rows = this.itemCheckTaskMapper.update(null, uw);
        if (rows == 0) {
            // 已被别处抢先处理：以档内为准重取，避免拿着旧对象误判"两笔齐全"
            TItemCheckTask fresh = this.itemCheckTaskMapper.selectById(r.getId());
            if (fresh != null) {
                r.setStatus(fresh.getStatus());
                r.setDispatchAt(fresh.getDispatchAt());
                r.setRoundNo(fresh.getRoundNo());
            }
        }
    }

    /**
     * 派不成：只写情形、缘故、本轮轮次；不碰 due_at / dispatch_at
     *（撤回这一笔查得着，头回的日子与派出时刻不许被盖掉）。
     */
    private void markFail(TItemCheckTask r, String reason, String roundNo) {
        r.setStatus(STATUS_FAIL);
        r.setFailReason(reason);
        r.setRoundNo(roundNo);
        UpdateWrapper<TItemCheckTask> uw = new UpdateWrapper<TItemCheckTask>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .set("status", STATUS_FAIL)
                .set("fail_reason", reason)
                .set("round_no", roundNo)
                .set("update_by", "Task")
                .set("update_time", new Date());
        this.itemCheckTaskMapper.update(null, uw);
    }

    // ------------------------------------------------------------------
    // 同一把尺与小工具
    // ------------------------------------------------------------------

    /** 尺子：止点到没到——due_at &lt;= at（含当日止点）。翻单与回写复核都用它。 */
    private boolean dueBy(TItemCheckTask r, Date at) {
        if (r == null || at == null || r.getDueAt() == null) {
            return false;
        }
        return CheckDutyClock.ts(r.getDueAt()).compareTo(CheckDutyClock.ts(at)) <= 0;
    }

    private boolean isWaiting(TItemCheckTask r) {
        return r != null && (r.getStatus() == null || r.getStatus() == STATUS_WAIT);
    }

    private TItemDeptArch findArch(String siteNo) {
        if (isBlank(siteNo)) {
            return null;
        }
        return this.itemDeptArchMapper.selectOne(new QueryWrapper<TItemDeptArch>()
                .eq("del_flag", 0)
                .eq("site_no", siteNo.trim())
                .last("limit 1"));
    }

    /** 岁末半月用：当日已落地（已派出且派出时刻落在今天）的事项去重键。 */
    private List<String> loadLandedKeysToday(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        Date from = c.getTime();
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        Date to = c.getTime();
        List<TItemCheckTask> landed = this.itemCheckTaskMapper.selectList(new QueryWrapper<TItemCheckTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_DONE)
                .isNotNull("dispatch_at")
                .ge("dispatch_at", CheckDutyClock.ts(from))
                .le("dispatch_at", CheckDutyClock.ts(to)));
        List<String> keys = new ArrayList<String>();
        for (TItemCheckTask t : landed) {
            keys.add(dedupKey(t));
        }
        return keys;
    }

    /** 同一事项的去重键：事项码+承办部门+事由归类 */
    private static String dedupKey(TItemCheckTask t) {
        return str(t.getItemCode()) + "|" + str(t.getSiteNo()) + "|" + t.getReasonType();
    }

    private static String str(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Date copy(Date d) {
        return d == null ? null : new Date(d.getTime());
    }

    /** 止点往前挪 amount 日（能挪的只有提前开口那一栏，止点本身一个毫秒都不动）。 */
    private static Date minusDays(Date dueAt, BigDecimal amount) {
        if (dueAt == null) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(dueAt);
        c.add(Calendar.DAY_OF_MONTH, -amount.intValue());
        return c.getTime();
    }

    private static String buildContent(TItemCheckTask t, String failReason) {
        StringBuilder sb = new StringBuilder();
        sb.append(reasonName(t.getReasonType()));
        if (!isBlank(t.getSiteName())) {
            sb.append("｜被派：").append(t.getSiteName().trim());
        }
        if (failReason != null) {
            sb.append("｜").append(failReason);
        }
        return sb.length() > 255 ? sb.substring(0, 255) : sb.toString();
    }

    /** 事由与办法照什么写——三桩各归各类 */
    private static String reasonName(Integer reasonType) {
        if (reasonType == null) {
            return "要素定期核查";
        }
        switch (reasonType) {
            case REASON_ELEMENT:
                return "要素同上位名录不合核查";
            case REASON_LIMIT:
                return "承诺时限同实际办件接不平核查";
            case REASON_HOOK:
                return "挂接口径没对上核查";
            default:
                return "要素定期核查";
        }
    }

    /** 派单号：PD + 日戳 + 当日三位序号；撤回重派另起一号也走它。 */
    private static String nextItemNo(String dayStamp, int[] seq) {
        seq[0]++;
        return String.format("PD%s%03d", dayStamp, seq[0]);
    }

    private static String dayStamp(Date d) {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(d);
    }

    /** 挑出次序：止点早的在前、同止点按 id（确定性，岁末"头一次落地"才有准） */
    private static final Comparator<TItemCheckTask> DUE_THEN_ID_ASC = new Comparator<TItemCheckTask>() {
        @Override
        public int compare(TItemCheckTask a, TItemCheckTask b) {
            int cmp = CheckDutyClock.ts(a.getDueAt()).compareTo(CheckDutyClock.ts(b.getDueAt()));
            if (cmp != 0) {
                return cmp;
            }
            long ia = a.getId() == null ? Long.MAX_VALUE : a.getId();
            long ib = b.getId() == null ? Long.MAX_VALUE : b.getId();
            return Long.compare(ia, ib);
        }
    };
}
