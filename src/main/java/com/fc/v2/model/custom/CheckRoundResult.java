package com.fc.v2.model.custom;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 一轮核查派单执行后的局面：候派／已派出／派不成三笔，由同一回执行点算落库后交出。
 * 屏上只报本轮；三笔与档里逐条点出的三笔同出一回，对不齐即整回不算成。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ApiModel(value = "CheckRoundResult", description = "本轮核查派单局面")
public class CheckRoundResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本轮轮次号（派出条目同笔钉住的那一笔） */
    @ApiModelProperty(value = "本轮轮次号")
    private String roundNo;

    /** 这一回执行照的时刻（同一把尺两头都用它） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "执行时刻")
    private Date runAt;

    /** 本轮是不是落在值守开口段；段外（夜里/法定节假日）不作声，有到期也只压着 */
    @ApiModelProperty(value = "是否落在值守开口段")
    private boolean windowOpen;

    /** 岁末半月班表：同日同事项第二回及以后不入账的条数（先落下的原样留着） */
    @ApiModelProperty(value = "岁末同日重复不入账条数")
    private int duplicateHeld;

    /** 本轮挑出该动的条数（同一把尺翻出来的） */
    @ApiModelProperty(value = "本轮挑出条数")
    private int picked;

    /** 段外/未到开口压住的条数 */
    @ApiModelProperty(value = "压住条数")
    private int held;

    /** 三笔之一：本轮局面里仍是候派的（压住、岁末重复不入账都在此笔） */
    @ApiModelProperty(value = "本轮候派笔数")
    private int waitingCount;

    /** 三笔之二：本轮已派出条数（情形改已派出与钉住时刻两笔齐全才算） */
    @ApiModelProperty(value = "本轮已派出笔数")
    private int dispatchedCount;

    /** 三笔之三：本轮派不成条数（撤并/承办空着/过期撤回，缘故随行走） */
    @ApiModelProperty(value = "本轮派不成笔数")
    private int failedCount;

    /** 给屏上的一句话：非开口时段／本轮无事／本轮三笔 */
    @ApiModelProperty(value = "本轮结论一句话")
    private String message;

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public Date getRunAt() {
        return runAt;
    }

    public void setRunAt(Date runAt) {
        this.runAt = runAt;
    }

    public boolean isWindowOpen() {
        return windowOpen;
    }

    public void setWindowOpen(boolean windowOpen) {
        this.windowOpen = windowOpen;
    }

    public int getDuplicateHeld() {
        return duplicateHeld;
    }

    public void setDuplicateHeld(int duplicateHeld) {
        this.duplicateHeld = duplicateHeld;
    }

    public int getPicked() {
        return picked;
    }

    public void setPicked(int picked) {
        this.picked = picked;
    }

    public int getHeld() {
        return held;
    }

    public void setHeld(int held) {
        this.held = held;
    }

    public int getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(int waitingCount) {
        this.waitingCount = waitingCount;
    }

    public int getDispatchedCount() {
        return dispatchedCount;
    }

    public void setDispatchedCount(int dispatchedCount) {
        this.dispatchedCount = dispatchedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
