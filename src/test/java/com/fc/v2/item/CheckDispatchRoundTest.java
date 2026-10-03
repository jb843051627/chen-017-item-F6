package com.fc.v2.item;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemCheckTaskMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.mapper.auto.TItemDutyWindowMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemCheckTask;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.model.auto.TItemDutyWindow;
import com.fc.v2.model.custom.CheckRoundResult;
import com.fc.v2.service.impl.TItemCheckTaskServiceImpl;
import com.fc.v2.service.support.CheckDutyClock;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 要素定期核查派单一轮跑法集成测试：H2(MySQL 方言) + 真实 MyBatis-Plus Mapper，
 * 不起 Spring 容器（避开 shiro/redis），服务层照生产装配。
 */
class CheckDispatchRoundTest {

    private SqlSession session;
    private TItemCheckTaskMapper taskMapper;
    private TItemDutyWindowMapper dutyMapper;
    private TItemDeptArchMapper archMapper;
    private TItemCatalogRowMapper rowMapper;
    private TItemCheckTaskServiceImpl service;

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:check_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        schema(ds);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        cfg.addMapper(TItemCheckTaskMapper.class);
        cfg.addMapper(TItemDutyWindowMapper.class);
        cfg.addMapper(TItemDeptArchMapper.class);
        cfg.addMapper(TItemCatalogRowMapper.class);

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        fb.setConfiguration(cfg);
        fb.setGlobalConfig(globalConfig);
        SqlSessionFactory factory = fb.getObject();
        this.session = factory.openSession(true);
        this.taskMapper = session.getMapper(TItemCheckTaskMapper.class);
        this.dutyMapper = session.getMapper(TItemDutyWindowMapper.class);
        this.archMapper = session.getMapper(TItemDeptArchMapper.class);
        this.rowMapper = session.getMapper(TItemCatalogRowMapper.class);

        CheckDutyClock clock = new CheckDutyClock();
        set(clock, "dutyWindowMapper", dutyMapper);

        this.service = new TItemCheckTaskServiceImpl();
        set(service, "itemCheckTaskMapper", taskMapper);
        set(service, "itemCatalogRowMapper", rowMapper);
        set(service, "itemDeptArchMapper", archMapper);
        set(service, "dutyClock", clock);

        seedArch(0L, "ZW00", "在编部门", 0);
        seedArch(1L, "ZW01", "已撤并部门", 1);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ---------------------------------------------------------------

    @Test
    @DisplayName("同一把尺边界：止点时刻当刻派出，止点前一秒压住且报本轮无事")
    void dueBoundary() {
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-03-15 10:00:00"), 0);

        CheckRoundResult before = service.runRound(t("2026-03-15 09:59:59"));
        assertTrue(before.isWindowOpen());
        assertEquals(0, before.getPicked());
        assertEquals(0, before.getDispatchedCount());
        assertEquals(0, before.getWaitingCount());
        assertEquals("本轮无事", before.getMessage());
        assertEquals(Integer.valueOf(0), taskMapper.selectList(null).get(0).getStatus());

        CheckRoundResult at = service.runRound(t("2026-03-15 10:00:00"));
        assertEquals(1, at.getDispatchedCount());
        assertEquals(0, at.getFailedCount());
        assertEquals(0, at.getWaitingCount());
        TItemCheckTask r = taskMapper.selectList(null).get(0);
        assertEquals(Integer.valueOf(1), r.getStatus());
        assertEquals("2026-03-15 10:00:00", ts(r.getDispatchAt()));
        assertEquals(at.getRoundNo(), r.getRoundNo());
    }

    @Test
    @DisplayName("夜里段外不作声，到期压着；当日开口段内下一回照派（不算过期撤回）")
    void nightHoldsThenDispatchesSameDay() {
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-03-15 08:00:00"), 0);

        CheckRoundResult night = service.runRound(t("2026-03-15 08:30:00"));
        assertFalse(night.isWindowOpen());
        assertEquals(1, night.getHeld());
        assertEquals(1, night.getWaitingCount());
        assertEquals(0, night.getDispatchedCount());
        assertEquals(Integer.valueOf(0), taskMapper.selectList(null).get(0).getStatus());

        CheckRoundResult morning = service.runRound(t("2026-03-15 09:30:00"));
        assertEquals(1, morning.getDispatchedCount());
        assertEquals(0, morning.getFailedCount());
    }

    @Test
    @DisplayName("法定节假日整日静默压着，节后首个开口段照派（不当作过期）")
    void holidayHeldThenDispatchedNextWorkday() {
        // 2026-03-15 周日标成法定节假日（整日静默）
        addDuty("2026-03-15", 0, "00:00:00", "23:59:59");
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-03-15 10:00:00"), 0);

        CheckRoundResult holiday = service.runRound(t("2026-03-15 10:30:00"));
        assertFalse(holiday.isWindowOpen());
        assertEquals(1, holiday.getHeld());
        assertEquals(Integer.valueOf(0), taskMapper.selectList(null).get(0).getStatus());

        CheckRoundResult next = service.runRound(t("2026-03-16 10:00:00"));
        assertTrue(next.isWindowOpen());
        assertEquals(1, next.getDispatchedCount());
        assertEquals(0, next.getFailedCount());
    }

    @Test
    @DisplayName("岁末半月：同一事项当日第二回不入账，先落下的原样留着")
    void yearEndSameItemOncePerDay() {
        addWaiting("PD1", "ITEM-X", "ZW00", 1, t("2026-12-16 09:00:00"), 0);
        addWaiting("PD2", "ITEM-X", "ZW00", 1, t("2026-12-16 09:00:00"), 0);
        addWaiting("PD3", "ITEM-Y", "ZW00", 2, t("2026-12-16 09:00:00"), 0);

        CheckRoundResult r = service.runRound(t("2026-12-16 09:30:00"));
        assertEquals(2, r.getDispatchedCount());
        assertEquals(1, r.getDuplicateHeld());
        assertEquals(1, r.getWaitingCount());
        assertEquals(0, r.getFailedCount());

        // 原样留着：仍是候派，下一个非岁末/次日还可走
        TItemCheckTask second = findByNo("PD2");
        assertEquals(Integer.valueOf(0), second.getStatus());
        assertNull(second.getDispatchAt());
    }

    @Test
    @DisplayName("寻常日子同事项同日两单都派（岁末幂等只在那半个月用）")
    void normalDaysNoDedup() {
        addWaiting("PD1", "ITEM-X", "ZW00", 1, t("2026-03-15 09:00:00"), 0);
        addWaiting("PD2", "ITEM-X", "ZW00", 1, t("2026-03-15 09:00:00"), 0);

        CheckRoundResult r = service.runRound(t("2026-03-15 09:30:00"));
        assertEquals(2, r.getDispatchedCount());
        assertEquals(0, r.getWaitingCount());
    }

    @Test
    @DisplayName("已派出的下一轮不再当候派挑出")
    void dispatchedNotPickedAgain() {
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-03-15 10:00:00"), 0);

        CheckRoundResult first = service.runRound(t("2026-03-15 10:00:00"));
        assertEquals(1, first.getDispatchedCount());

        CheckRoundResult second = service.runRound(t("2026-03-15 11:00:00"));
        assertEquals(0, second.getPicked());
        assertEquals(0, second.getDispatchedCount());
        assertEquals("本轮无事", second.getMessage());
    }

    @Test
    @DisplayName("派不成两情形：部门撤并、承办栏整季空着；屏上翻得着、后面的照走")
    void failReasonsDoNotStopRound() {
        addWaiting("PD1", "ITEM-A", "ZW01", 1, t("2026-03-15 10:00:00"), 0);
        addWaiting("PD2", "ITEM-B", null, 2, t("2026-03-15 10:00:00"), 0);
        addWaiting("PD3", "ITEM-C", "ZW00", 3, t("2026-03-15 10:00:00"), 0);

        CheckRoundResult r = service.runRound(t("2026-03-15 10:00:00"));
        assertEquals(1, r.getDispatchedCount());
        assertEquals(2, r.getFailedCount());
        assertEquals(0, r.getWaitingCount());
        assertTrue(findByNo("PD1").getFailReason().contains("撤并"));
        assertTrue(findByNo("PD2").getFailReason().contains("空着"));
        assertEquals(Integer.valueOf(1), findByNo("PD3").getStatus());
    }

    @Test
    @DisplayName("日子过了还没派：撤回转派不成；头回止点不被盖、无派出时刻")
    void overdueWithdrawKeepsOriginalDueAndDispatchStamp() {
        TItemCheckTask t = addWaiting("PD1", "ITEM-A", "ZW00", 1,
                t("2026-03-16 09:00:00"), 0);

        CheckRoundResult r = service.runRound(t("2026-03-18 10:00:00"));
        assertEquals(0, r.getDispatchedCount());
        assertEquals(1, r.getFailedCount());

        TItemCheckTask back = taskMapper.selectById(t.getId());
        assertEquals(Integer.valueOf(2), back.getStatus());
        assertTrue(back.getFailReason().contains("撤回"));
        assertEquals("2026-03-16 09:00:00", ts(back.getDueAt()));
        assertNull(back.getDispatchAt());
        assertNotNull(back.getRoundNo());
    }

    @Test
    @DisplayName("撤回重派：旧单留底转派不成，新单另起一号、从原单直出、止点不后挪")
    void reissueKeepsOldAndOpensNewNumber() {
        TItemCheckTask old = addWaiting("PD-OLD", "ITEM-A", "ZW00", 1,
                t("2026-03-20 10:00:00"), 3);

        TItemCheckTask nw = service.reissue(old.getId());
        assertNotNull(nw);
        assertFalse(nw.getItemNo().equals(old.getItemNo()));
        assertEquals("PD-OLD", nw.getSourceNo());
        assertEquals("ITEM-A", nw.getItemCode());
        assertEquals("ZW00", nw.getSiteNo());
        assertEquals(Integer.valueOf(1), nw.getReasonType());
        assertEquals("2026-03-20 10:00:00", ts(nw.getDueAt()));
        assertEquals(0, new BigDecimal("3").compareTo(nw.getAmount()));
        assertEquals(Integer.valueOf(0), nw.getStatus());

        TItemCheckTask oldBack = taskMapper.selectById(old.getId());
        assertEquals(Integer.valueOf(2), oldBack.getStatus());
        // 头回定的日子不被盖
        assertEquals("2026-03-20 10:00:00", ts(oldBack.getDueAt()));
        assertTrue(oldBack.getFailReason().contains(nw.getItemNo()));
    }

    @Test
    @DisplayName("名录一键开单：事项码/承办/止点/提前几日直带，不重复开；撤并与空承办就地派不成")
    void generateFromCatalogCarriesFieldsAndSkipsDupes() {
        addCatalogRow("B1", 1, "ITEM-A", "ZW00", 1, t("2026-06-30 17:00:00"), 5);
        addCatalogRow("B2", 2, "ITEM-B", "ZW01", 2, t("2026-06-30 17:00:00"), 0);
        addCatalogRow("B3", 3, "ITEM-C", null, 3, t("2026-06-30 17:00:00"), 0);

        List<TItemCheckTask> first = service.generateFromCatalog();
        assertEquals(3, first.size());
        TItemCheckTask a = findByItem("ITEM-A");
        assertEquals("ZW00", a.getSiteNo());
        assertEquals("在编部门", a.getSiteName());
        assertEquals(Integer.valueOf(0), a.getStatus());
        assertEquals("2026-06-25 17:00:00", ts(a.getOpenAt()));
        assertEquals(Integer.valueOf(2), findByItem("ITEM-B").getStatus());
        assertTrue(findByItem("ITEM-B").getFailReason().contains("撤并"));
        assertEquals(Integer.valueOf(2), findByItem("ITEM-C").getStatus());
        assertNull(findByItem("ITEM-C").getSiteNo());

        // 第二回一键：同一桩不另开
        assertEquals(0, service.generateFromCatalog().size());
        assertEquals(3, taskMapper.selectList(null).size());
    }

    @Test
    @DisplayName("三笔与档里逐条点出的三笔同一回算齐")
    void threeCountsMatchRows() {
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-03-15 10:00:00"), 0);
        addWaiting("PD2", "ITEM-B", "ZW01", 1, t("2026-03-15 10:00:00"), 0);
        addWaiting("PD3", "ITEM-C", null, 2, t("2026-03-15 10:00:00"), 0);
        // 岁末同日重复
        addWaiting("PD4", "ITEM-D", "ZW00", 1, t("2026-12-16 09:00:00"), 0);
        addWaiting("PD5", "ITEM-D", "ZW00", 1, t("2026-12-16 09:00:00"), 0);

        CheckRoundResult normal = service.runRound(t("2026-03-15 10:00:00"));
        long nDone = taskMapper.selectList(null).stream()
                .filter(x -> Integer.valueOf(1).equals(x.getStatus()) && normal.getRoundNo().equals(x.getRoundNo()))
                .count();
        long nFail = taskMapper.selectList(null).stream()
                .filter(x -> Integer.valueOf(2).equals(x.getStatus()) && normal.getRoundNo().equals(x.getRoundNo()))
                .count();
        assertEquals(nDone, normal.getDispatchedCount());
        assertEquals(nFail, normal.getFailedCount());
        assertEquals(1, normal.getDispatchedCount());
        assertEquals(2, normal.getFailedCount());

        CheckRoundResult yearEnd = service.runRound(t("2026-12-16 09:30:00"));
        assertEquals(1, yearEnd.getDispatchedCount());
        assertEquals(1, yearEnd.getWaitingCount());
    }

    @Test
    @DisplayName("岁末幂等只管当日：次日同一事项照样落头一回")
    void yearEndDedupResetsNextDay() {
        addWaiting("PD1", "ITEM-X", "ZW00", 1, t("2026-12-16 09:00:00"), 0);
        CheckRoundResult d16 = service.runRound(t("2026-12-16 09:30:00"));
        assertEquals(1, d16.getDispatchedCount());

        addWaiting("PD2", "ITEM-X", "ZW00", 1, t("2026-12-17 09:00:00"), 0);
        CheckRoundResult d17 = service.runRound(t("2026-12-17 09:30:00"));
        assertEquals(1, d17.getDispatchedCount());
        assertEquals(Integer.valueOf(1), findByNo("PD2").getStatus());
    }

    @Test
    @DisplayName("岁末班表开口时辰另切：班表把当日开口配成晚间段，寻常夜里规矩让路")
    void yearEndCustomShiftBeatsNormalNight() {
        addDuty("2026-12-20", 2, "20:00:00", "22:00:00");
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-12-20 20:30:00"), 0);

        // 寻常规矩此刻是夜里；岁末班表开口 20:00–22:00，作声
        CheckRoundResult r = service.runRound(t("2026-12-20 20:30:00"));
        assertTrue(r.isWindowOpen());
        assertEquals(1, r.getDispatchedCount());
    }

    @Test
    @DisplayName("撤回重派后的新单止点未到先候着，到点在开口段照派；旧单仍留底派不成")
    void reissuedTaskDispatchesInLaterRound() {
        TItemCheckTask old = addWaiting("PD-OLD", "ITEM-A", "ZW00", 1,
                t("2026-03-16 09:00:00"), 0);
        TItemCheckTask nw = service.reissue(old.getId());
        assertNotNull(nw);

        CheckRoundResult r = service.runRound(t("2026-03-16 10:00:00"));
        assertEquals(1, r.getDispatchedCount());
        TItemCheckTask nwBack = taskMapper.selectById(nw.getId());
        assertEquals(Integer.valueOf(1), nwBack.getStatus());
        assertEquals("2026-03-16 10:00:00", ts(nwBack.getDispatchAt()));
        assertEquals(Integer.valueOf(2), taskMapper.selectById(old.getId()).getStatus());
    }

    @Test
    @DisplayName("岁末去重只认真落地：同事项头一条撤回落派不成，不连累第二条仍照常处置")
    void yearEndFailDoesNotPoisonDedup() {
        // 两条同事项同承办，止点在岁末前且开口轮次已错过——头一条撤回，第二条也应照撤回，
        // 绝不能被当成"当日第二回"压住
        addWaiting("PD1", "ITEM-Z", "ZW00", 1, t("2026-12-10 09:00:00"), 0);
        addWaiting("PD2", "ITEM-Z", "ZW00", 1, t("2026-12-10 09:00:00"), 0);

        CheckRoundResult r = service.runRound(t("2026-12-16 10:00:00"));
        assertEquals(0, r.getDispatchedCount());
        assertEquals(2, r.getFailedCount());
        assertEquals(0, r.getDuplicateHeld());
        assertTrue(findByNo("PD1").getFailReason().contains("撤回"));
        assertTrue(findByNo("PD2").getFailReason().contains("撤回"));
    }

    @Test
    @DisplayName("已派出的单不许撤回重派")
    void dispatchedCannotBeReissued() {
        TItemCheckTask t = addWaiting("PD1", "ITEM-A", "ZW00", 1,
                t("2026-03-15 10:00:00"), 0);
        service.runRound(t("2026-03-15 10:00:00"));
        assertNull(service.reissue(t.getId()));
        assertEquals(1, taskMapper.selectList(null).size());
    }

    @Test
    @DisplayName("旧跑法 runOnce 入参返回照旧：段外0、派出回已派出数")
    void legacyRunOnceShapeUnchanged() {
        addWaiting("PD1", "ITEM-A", "ZW00", 1, t("2026-03-15 10:00:00"), 0);
        assertEquals(0, service.runOnce(t("2026-03-15 08:00:00")));
        assertEquals(1, service.runOnce(t("2026-03-15 10:00:00")));
        assertEquals(0, service.runOnce(null));
    }

    @Test
    @DisplayName("跑不起来与本轮无事不是一回事：时刻为空直接报错，不返回无事")
    void nullTimeIsNotIdle() {
        try {
            service.runRound(null);
            throw new AssertionError("应当抛出非法参数");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("不能为空"));
        }
    }

    // ---------------------------------------------------------------

    private TItemCheckTask addWaiting(String no, String itemCode, String siteNo, int reason,
                                      Date due, int amountDays) {
        TItemCheckTask t = new TItemCheckTask();
        t.setItemNo(no);
        t.setItemCode(itemCode);
        t.setSiteNo(siteNo);
        t.setReasonType(reason);
        t.setDueAt(due);
        t.setAmount(new BigDecimal(amountDays));
        t.setStatus(0);
        t.setDelFlag(0);
        taskMapper.insert(t);
        return t;
    }

    private void addDuty(String date, int kind, String from, String to) {
        TItemDutyWindow w = new TItemDutyWindow();
        w.setCalDate(t(date + " 00:00:00"));
        w.setDayKind(kind);
        w.setOpenFrom(java.sql.Time.valueOf(from));
        w.setOpenTo(java.sql.Time.valueOf(to));
        w.setDelFlag(0);
        dutyMapper.insert(w);
    }

    private void addCatalogRow(String batch, int rowNo, String itemCode, String siteNo,
                               int reason, Date due, int amountDays) {
        TItemCatalogRow r = new TItemCatalogRow();
        r.setBatchNo(batch);
        r.setRowNo(rowNo);
        r.setItemCode(itemCode);
        r.setSiteNo(siteNo);
        r.setReasonType(reason);
        r.setDueAt(due);
        r.setAmount(new BigDecimal(amountDays));
        r.setStatus(0);
        r.setDelFlag(0);
        rowMapper.insert(r);
    }

    private void seedArch(long id, String no, String name, int status) {
        TItemDeptArch a = new TItemDeptArch();
        a.setId(id);
        a.setSiteNo(no);
        a.setSiteName(name);
        a.setStatus(status);
        a.setDelFlag(0);
        archMapper.insert(a);
    }

    private TItemCheckTask findByNo(String no) {
        List<TItemCheckTask> all = taskMapper.selectList(null);
        for (TItemCheckTask x : all) {
            if (no.equals(x.getItemNo())) {
                return x;
            }
        }
        return null;
    }

    private TItemCheckTask findByItem(String itemCode) {
        List<TItemCheckTask> all = taskMapper.selectList(null);
        for (TItemCheckTask x : all) {
            if (itemCode.equals(x.getItemCode())) {
                return x;
            }
        }
        return null;
    }

    private static void schema(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_item_check_task ("
                    + "id bigint PRIMARY KEY, item_no varchar(64), source_no varchar(64), "
                    + "item_code varchar(64), site_no varchar(64), site_name varchar(128), "
                    + "reason_type int, due_at datetime, open_at datetime, amount decimal(12,2), "
                    + "content varchar(255), round_no varchar(32), dispatch_at datetime, "
                    + "fail_reason varchar(255), status int, del_flag int, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), "
                    + "update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_item_duty_window ("
                    + "id bigint PRIMARY KEY, cal_date date, day_kind int, "
                    + "open_from time, open_to time, del_flag int, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), "
                    + "update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_item_dept_arch ("
                    + "id bigint PRIMARY KEY, site_no varchar(64), site_name varchar(128), "
                    + "site_type varchar(32), road_name varchar(128), "
                    + "th1_max decimal(12,2), th2_max decimal(12,2), th3_max decimal(12,2), "
                    + "status int, del_flag int, create_by varchar(64), create_time datetime, "
                    + "update_by varchar(64), update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_item_catalog_row ("
                    + "id bigint PRIMARY KEY, batch_no varchar(64), row_no int, "
                    + "item_code varchar(64), site_no varchar(64), reason_type int, "
                    + "due_at datetime, amount decimal(12,2), qty decimal(12,2), status int, "
                    + "del_flag int, create_by varchar(64), create_time datetime, "
                    + "update_by varchar(64), update_time datetime, remark varchar(500))");
        }
    }
}
