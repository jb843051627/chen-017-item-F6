package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 核查派单值守开口班表对象 t_item_duty_window
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_item_duty_window")
@ApiModel(value = "TItemDutyWindow", description = "核查派单值守开口班表")
public class TItemDutyWindow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 班表所属日(自然日,一日一行) */
    @TableField("cal_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "班表所属日")
    private Date calDate;

    /** 当日班别 0整日静默(法定节假日) 1寻常值守开口 2岁末半月班表开口 */
    @TableField("day_kind")
    @ApiModelProperty(value = "当日班别 0整日静默 1寻常值守 2岁末半月班表")
    private Integer dayKind;

    /** 开口时辰起(段外即夜里,不作声) */
    @TableField("open_from")
    @ApiModelProperty(value = "开口时辰起")
    private java.sql.Time openFrom;

    /** 开口时辰止 */
    @TableField("open_to")
    @ApiModelProperty(value = "开口时辰止")
    private java.sql.Time openTo;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getCalDate() {
        return calDate;
    }

    public void setCalDate(Date calDate) {
        this.calDate = calDate;
    }

    public Integer getDayKind() {
        return dayKind;
    }

    public void setDayKind(Integer dayKind) {
        this.dayKind = dayKind;
    }

    public java.sql.Time getOpenFrom() {
        return openFrom;
    }

    public void setOpenFrom(java.sql.Time openFrom) {
        this.openFrom = openFrom;
    }

    public java.sql.Time getOpenTo() {
        return openTo;
    }

    public void setOpenTo(java.sql.Time openTo) {
        this.openTo = openTo;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
