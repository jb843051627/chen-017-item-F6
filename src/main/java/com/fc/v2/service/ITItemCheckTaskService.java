package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TItemCheckTask;
import com.fc.v2.model.custom.CheckRoundResult;

/**
 * 要素定期核查派单 Service接口（scheduling-job 形状：周期执行）。
 *
 * 旧三跑法（selectTItemCheckTaskById/listDue/runOnce）入参与返回一概照旧；
 * 新跑法（名录一键开单、一轮执行局面、撤回重派）在另名下另叠：
 *   generateFromCatalog() / runRound(Date) / reissue(Long)。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemCheckTaskService {

    /** 按主键回查条目 */
    TItemCheckTask selectTItemCheckTaskById(Long id);

    /** 台账翻查（只读；分页由调用方统一 startPage，排序/条件由 wrapper 带） */
    List<TItemCheckTask> selectTItemCheckTaskList(Wrapper<TItemCheckTask> queryWrapper);

    /**
     * 同一把尺翻单：候派（status=0）且该动手那一日的止点已到（due_at &lt;= at）。
     * 尺子只认止点到没到，不认值守时段——那是出手时辰的规矩，归 runRound。
     */
    List<TItemCheckTask> listDue(Date at);

    /** 旧跑法：执行一次，返回**已派出条数**；段外静默返回 0；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 新跑法：名录里该核的条目照口径一键开单。
     * 事项码、承办部门、该动手那一日、提前几日一并从名录条目直带，不另请一人抄第二遍；
     * 同一桩（事项码+承办部门+事由+止点）已开过候派/已派出单的不重复开。
     * 返回新开出的候派单。
     */
    List<TItemCheckTask> generateFromCatalog();

    /**
     * 新跑法：照时刻跑一轮，定哪几桩该出手、出去几条、压住几条，算定再落库。
     * 返回本轮局面（候派／已派出／派不成三笔，同回点算）。
     */
    CheckRoundResult runRound(Date at);

    /**
     * 新跑法：撤回重派。旧单就地转派不成并留底（头回止点与真正派出时刻原样留着，
     * 不许盖掉），新单从原单直出、另起一号；能挪的只有提前几日那一栏。
     */
    TItemCheckTask reissue(Long sourceId);
}
