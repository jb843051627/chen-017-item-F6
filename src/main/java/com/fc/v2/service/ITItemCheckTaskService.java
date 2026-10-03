package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TItemCheckTask;
import com.fc.v2.model.custom.CheckRoundBook;

/**
 * 要素定期核查派单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemCheckTaskService {

    /** 按主键回查条目 */
    TItemCheckTask selectTItemCheckTaskById(Long id);

    /** 该时刻可处理的条目（执行窗口内 + 到期 + 尚未处理） */
    List<TItemCheckTask> listDue(Date at);

    /** 执行一次，返回**成功条数**；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 执行一次并交出本轮局面（同名底下另叠的跑法）。
     * 挑出几条、出去几条、派不成几条、压住几条，与落库同一回点算；
     * 返回**成功条数**，与 {@link #runOnce(Date)} 同一把尺。
     */
    int runOnce(Date at, CheckRoundBook book);

    /**
     * 撤回扫尾：日子过了还没派出去的条目转成派不成，撤回这一笔查得着。
     * 只动情形与缘故——头回定的日子与真正派出的时刻不许被后来的盖掉。
     * 返回撤回条数。
     */
    int withdrawOverdue(Date at);

    /**
     * 照名录一键生成本轮派单：事项码、该动手那一日（算到当日的止点）从条目带过去，
     * 不另请一人抄第二遍；已挂接的条目不重出。返回生成条数。
     */
    int generateFromCatalog(Date dueDay);

    /**
     * 撤回重派：从原单直出，旧单留底，新单另起一号。
     * 该动手那一日按新一轮的日子算到止点，提前几日一栏照原单带过去。
     * 返回新单主键；原单不在、未派不成或入参为空时返回 null。
     */
    Long redispatch(Long oldId, Date newDueDay);
}
