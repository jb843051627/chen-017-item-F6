package com.fc.v2.common.quartz.task;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.model.custom.CheckRoundBook;
import com.fc.v2.service.ITItemCheckTaskService;

/**
 * 要素定期核查派单的定时调度工作类：一季一轮，系统照日子去跑。
 * cron 由 sys_quartz_job 配置，调 bean 名 itemCheckTask。
 *
 * @CLASSNAME   ItemCheckTask
 * @Auther fuce
 * @DATE 2026-10-03
 */
@Component("itemCheckTask")
public class ItemCheckTask {

    @Autowired
    private ITItemCheckTaskService itemCheckTaskService;

    /**
     * 跑一轮：该动手那一日到了的归进本轮，段外只压着。
     * 本轮无事（回执 picked=0）属正常局面，不是跑不起来。
     */
    public void runRound() {
        CheckRoundBook book = new CheckRoundBook();
        itemCheckTaskService.runOnce(new Date(), book);
        System.out.println("核查派单本轮局面：挑出 " + book.getPicked()
                + "，已派出 " + book.getDispatched()
                + "，派不成 " + book.getFailed()
                + "，压住 " + book.getHeld()
                + (book.isNothingThisRound() ? "（本轮无事）" : ""));
    }

    /**
     * 撤回扫尾：日子过了还没派出去的转成派不成，撤回这一笔查得着。
     */
    public void withdrawOverdue() {
        int n = itemCheckTaskService.withdrawOverdue(new Date());
        System.out.println("核查派单撤回扫尾：" + n + " 条转派不成");
    }
}
