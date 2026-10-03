package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 核查一轮的局面回执：本轮挑出几条、出去几条、派不成几条、压住几条。
 *
 * <p>三笔数由服务层跑这一回时同一遍点算交出，屏上照此报本轮；
 * 档里逐条数出的三笔与此同出一回，不接不平。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckRoundBook implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本轮是否赶上出手段（false=段外压着，等下一轮，不算本轮无事） */
    private boolean windowOpen;

    /** 本轮挑出的该动条目数（到期且候派，岁末去重后） */
    private int picked;

    /** 本轮已派出条数（情形改已派出且派出时刻已钉住，两件齐才算） */
    private int dispatched;

    /** 本轮派不成条数（部门撤并、承办空缺、撤回等，缘故落在行上） */
    private int failed;

    /** 本轮压住条数（岁末同事项二回等，原样留着仍候派；段外压着的也算在这里） */
    private int held;

    public boolean isWindowOpen() {
        return windowOpen;
    }

    public void setWindowOpen(boolean windowOpen) {
        this.windowOpen = windowOpen;
    }

    public int getPicked() {
        return picked;
    }

    public void setPicked(int picked) {
        this.picked = picked;
    }

    public int getDispatched() {
        return dispatched;
    }

    public void setDispatched(int dispatched) {
        this.dispatched = dispatched;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getHeld() {
        return held;
    }

    public void setHeld(int held) {
        this.held = held;
    }

    /**
     * 本轮无事：窗口开着、也确实没挑出该动的。
     * 与"跑不起来"（执行异常）不是一回事，与"段外压着"也不是一回事。
     */
    public boolean isNothingThisRound() {
        return windowOpen && picked == 0;
    }
}
