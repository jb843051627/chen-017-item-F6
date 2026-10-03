package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemCheckTaskMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemCheckTask;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.model.custom.CheckRoundBook;
import com.fc.v2.service.impl.TItemCheckTaskServiceImpl;

/**
 * 要素定期核查派单：一把尺、执行窗口、岁末班表、回写两件套、单条失败不趴下。
 */
public class TItemCheckTaskServiceImplTest {

    private TItemCheckTaskMapper taskMapper;
    private TItemDeptArchMapper deptMapper;
    private TItemCatalogRowMapper catalogMapper;
    private TItemCheckTaskServiceImpl impl;

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TItemCheckTask row(long id, String itemNo, String due, String content) {
        TItemCheckTask t = new TItemCheckTask();
        t.setId(id);
        t.setItemNo(itemNo);
        t.setDueAt(due == null ? null : d(due));
        t.setContent(content);
        t.setStatus(0);
        t.setDelFlag(0);
        return t;
    }

    @BeforeEach
    public void setUp() {
        taskMapper = mock(TItemCheckTaskMapper.class);
        deptMapper = mock(TItemDeptArchMapper.class);
        catalogMapper = mock(TItemCatalogRowMapper.class);
        impl = new TItemCheckTaskServiceImpl();
        ReflectionTestUtils.setField(impl, "itemCheckTaskMapper", taskMapper);
        ReflectionTestUtils.setField(impl, "itemDeptArchMapper", deptMapper);
        ReflectionTestUtils.setField(impl, "itemCatalogRowMapper", catalogMapper);
    }

    @Test
    public void listDue_恰好到期归进本轮_未到期不动() {
        List<TItemCheckTask> rows = Arrays.asList(
                row(1L, "HC1", "2026-03-31 10:00:00", "事项甲"),
                row(2L, "HC2", "2026-03-31 10:00:01", "事项乙"),
                row(3L, "HC3", null, "事项丙"));
        when(taskMapper.selectList(any())).thenReturn(rows);

        List<TItemCheckTask> due = impl.listDue(d("2026-03-31 10:00:00"));
        assertEquals(1, due.size());
        assertEquals(Long.valueOf(1L), due.get(0).getId());

        // 候派与未删除的过滤沉在同一张查询里（一把尺的入口只有这一处）
        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(taskMapper).selectList(cap.capture());
        String where = cap.getValue().getSqlSegment();
        assertTrue(where.contains("del_flag"));
        assertTrue(where.contains("status"));
    }

    @Test
    public void listDue_夜里与法定节假日不作声_段外压着() {
        List<TItemCheckTask> rows = Arrays.asList(row(1L, "HC1", "2026-03-30 09:00:00", "事项甲"));
        when(taskMapper.selectList(any())).thenReturn(rows);

        // 夜里 02:00–05:00：到期也压着
        assertTrue(impl.listDue(d("2026-03-31 02:00:00")).isEmpty());
        assertTrue(impl.listDue(d("2026-03-31 04:59:59")).isEmpty());
        // 法定节假日：全天不作声
        assertTrue(impl.listDue(d("2026-10-01 10:00:00")).isEmpty());
        // 段外不查库
        verify(taskMapper, never()).selectList(any());
        // 段口一开就归进本轮
        assertEquals(1, impl.listDue(d("2026-03-31 05:00:00")).size());
    }

    @Test
    public void runOnce_本轮无事返回零_不是跑不起来() {
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<TItemCheckTask>());
        CheckRoundBook book = new CheckRoundBook();
        int ok = impl.runOnce(d("2026-03-31 10:00:00"), book);
        assertEquals(0, ok);
        assertTrue(book.isWindowOpen());
        assertEquals(0, book.getPicked());
        assertTrue(book.isNothingThisRound());
    }

    @Test
    public void runOnce_派成两件事一条update里办完_不盖头回定的日子() {
        List<TItemCheckTask> rows = Arrays.asList(row(7L, "HC7", "2026-03-31 09:00:00", "事项甲 青屏市某局"));
        when(taskMapper.selectList(any())).thenReturn(rows);
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        int ok = impl.runOnce(d("2026-03-31 10:00:00"));
        assertEquals(1, ok);

        ArgumentCaptor<UpdateWrapper> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskMapper).update(isNull(), cap.capture());
        UpdateWrapper uw = cap.getValue();
        String set = uw.getSqlSet();
        // 情形改已派出 + 派出那一刻钉住，两件齐才算成
        assertTrue(set.contains("status"));
        assertTrue(set.contains("update_time"));
        // 头回定的日子不许被后来的盖掉
        assertFalse(set.contains("due_at"));
        assertTrue(uw.getParamNameValuePairs().containsValue(1));
        assertTrue(uw.getParamNameValuePairs().containsValue("2026-03-31 10:00:00"));
        // 只动候派行：本轮派出去过的，下一轮不会再被改写
        String where = uw.getSqlSegment();
        assertTrue(where.contains("status"));
        assertTrue(where.contains("del_flag"));
    }

    @Test
    public void runOnce_单条走不通不趴下_缘故落在行上() {
        TItemDeptArch dissolved = new TItemDeptArch();
        dissolved.setId(1L);
        dissolved.setSiteNo("ZW01");
        dissolved.setSiteName("市住房和城乡建设管理局");
        dissolved.setStatus(1);
        when(deptMapper.selectList(any())).thenReturn(Arrays.asList(dissolved));

        List<TItemCheckTask> rows = Arrays.asList(
                row(1L, "HC1", "2026-03-31 09:00:00", "  "),
                row(2L, "HC2", "2026-03-31 09:00:00", "事项乙 承办ZW01"),
                row(3L, "HC3", "2026-03-31 09:00:00", "事项丙 承办ZW00"));
        when(taskMapper.selectList(any())).thenReturn(rows);
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        CheckRoundBook book = new CheckRoundBook();
        int ok = impl.runOnce(d("2026-03-31 10:00:00"), book);

        // 一条趴不下一整轮：两条派不成，一条派出，成功条数只算派出的
        assertEquals(1, ok);
        assertEquals(3, book.getPicked());
        assertEquals(1, book.getDispatched());
        assertEquals(2, book.getFailed());

        ArgumentCaptor<UpdateWrapper> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskMapper, times(3)).update(isNull(), cap.capture());
        int failRows = 0;
        int doneRows = 0;
        for (UpdateWrapper uw : cap.getAllValues()) {
            if (uw.getSqlSet().contains("remark")) {
                failRows++;
                boolean hasReason = false;
                for (Object v : uw.getParamNameValuePairs().values()) {
                    if (v != null && v.toString().startsWith("派不成")) {
                        hasReason = true;
                    }
                }
                assertTrue(hasReason);
            } else {
                doneRows++;
            }
        }
        assertEquals(2, failRows);
        assertEquals(1, doneRows);
    }

    @Test
    public void runOnce_岁末另按班表_同一事项一轮只认头一次落地() {
        List<TItemCheckTask> rows = Arrays.asList(
                row(1L, "HC1", "2026-12-19 23:59:59", "事项甲"),
                row(2L, "HC1", "2026-12-20 01:00:00", "事项甲"),
                row(3L, "HC2", "2026-12-20 02:00:00", "事项乙"));
        when(taskMapper.selectList(any())).thenReturn(rows);
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        // 岁末半个月里不走夜里不作声那一句：03:00 照跑
        Date at = d("2026-12-20 03:00:00");
        assertEquals(2, impl.listDue(at).size());

        CheckRoundBook book = new CheckRoundBook();
        int ok = impl.runOnce(at, book);
        // 同事项第二回来的不入账、原样留着；另一事项照派
        assertEquals(2, ok);
        assertEquals(2, book.getPicked());
        assertEquals(1, book.getHeld());
        verify(taskMapper, times(2)).update(isNull(), any());
    }

    @Test
    public void withdrawOverdue_撤回查得着_旧时刻不被盖() {
        List<TItemCheckTask> rows = Arrays.asList(
                row(1L, "HC1", "2026-03-30 23:59:59", "事项甲"),
                row(2L, "HC2", "2026-04-01 23:59:59", "事项乙"));
        when(taskMapper.selectList(any())).thenReturn(rows);
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        int n = impl.withdrawOverdue(d("2026-03-31 10:00:00"));
        assertEquals(1, n);

        ArgumentCaptor<UpdateWrapper> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskMapper).update(isNull(), cap.capture());
        UpdateWrapper uw = cap.getValue();
        assertTrue(uw.getSqlSet().contains("remark"));
        assertFalse(uw.getSqlSet().contains("due_at"));
        assertTrue(uw.getParamNameValuePairs().containsValue(2));
    }

    @Test
    public void generateFromCatalog_照条目带出_出过的不再重出() {
        TItemCatalogRow row = new TItemCatalogRow();
        row.setId(5L);
        row.setBatchNo("2026Q1");
        row.setRowNo(3);
        row.setItemCode("ITEM-9");
        row.setStatus(0);
        when(catalogMapper.selectList(any())).thenReturn(Arrays.asList(row));
        when(taskMapper.selectCount(any())).thenReturn(0);
        when(taskMapper.insert(any())).thenReturn(1);

        int ok = impl.generateFromCatalog(d("2026-03-31 08:00:00"));
        assertEquals(1, ok);

        ArgumentCaptor<TItemCheckTask> cap = ArgumentCaptor.forClass(TItemCheckTask.class);
        verify(taskMapper).insert(cap.capture());
        TItemCheckTask t = cap.getValue();
        assertEquals("HC2026Q1-3", t.getItemNo());
        // 该动手那一日算到当日的止点
        assertEquals("2026-03-31 23:59:59", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(t.getDueAt()));
        assertTrue(t.getContent().contains("ITEM-9"));
        assertEquals(Integer.valueOf(0), t.getStatus());
        assertEquals(BigDecimal.ZERO, t.getAmount());
        // 条目挂接，下一轮不再重出
        verify(catalogMapper).update(isNull(), any());
    }

    @Test
    public void redispatch_旧单留底_新单另起一号() {
        TItemCheckTask old = row(9L, "HC9", "2026-03-31 23:59:59", "事项甲");
        old.setStatus(2);
        old.setAmount(new BigDecimal("3"));
        when(taskMapper.selectById(9L)).thenReturn(old);
        when(taskMapper.selectCount(any())).thenReturn(1).thenReturn(0);
        when(taskMapper.insert(any())).thenAnswer(inv -> {
            TItemCheckTask t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });

        Long newId = impl.redispatch(9L, d("2026-06-30 12:00:00"));
        assertEquals(Long.valueOf(99L), newId);

        ArgumentCaptor<TItemCheckTask> cap = ArgumentCaptor.forClass(TItemCheckTask.class);
        verify(taskMapper).insert(cap.capture());
        TItemCheckTask t = cap.getValue();
        assertEquals("HC9-R2", t.getItemNo());
        assertEquals("2026-06-30 23:59:59", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(t.getDueAt()));
        assertEquals(new BigDecimal("3"), t.getAmount());
        assertEquals(Integer.valueOf(0), t.getStatus());
        // 旧单留底：全程没有对旧单的 update
        verify(taskMapper, never()).update(isNull(), any());
    }

    @Test
    public void redispatch_原单未派不成则不出新单() {
        TItemCheckTask old = row(9L, "HC9", "2026-03-31 23:59:59", "事项甲");
        old.setStatus(1);
        when(taskMapper.selectById(9L)).thenReturn(old);
        assertNull(impl.redispatch(9L, d("2026-06-30 12:00:00")));
        verify(taskMapper, never()).insert(any());
        assertNotNull(impl.selectTItemCheckTaskById(9L));
    }
}
