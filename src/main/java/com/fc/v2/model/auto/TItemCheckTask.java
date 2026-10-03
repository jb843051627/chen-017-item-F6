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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 要素定期核查派单对象 t_item_check_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_item_check_task")
@ApiModel(value = "TItemCheckTask", description = "要素定期核查派单")
public class TItemCheckTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核查派单号 */
    @TableField("item_no")
    @ApiModelProperty(value = "核查派单号")
    private String itemNo;

    /** 重派来源旧派单号(撤回重派时旧单留底、新单另起一号) */
    @TableField("source_no")
    @ApiModelProperty(value = "重派来源旧派单号")
    private String sourceNo;

    /** 事项码(由名录条目直带,不另请人抄第二遍) */
    @TableField("item_code")
    @ApiModelProperty(value = "事项码")
    private String itemCode;

    /** 被派的承办部门底册代号(整季空着即此栏为NULL) */
    @TableField("site_no")
    @ApiModelProperty(value = "被派的承办部门底册代号")
    private String siteNo;

    /** 被派的承办部门全称(开单时自底册带过来留底) */
    @TableField("site_name")
    @ApiModelProperty(value = "被派的承办部门全称")
    private String siteName;

    /** 事由归类 1要素同上位名录不合 2承诺时限同实际办件接不平 3挂接口径没对上 */
    @TableField("reason_type")
    @ApiModelProperty(value = "事由归类 1要素不合 2时限接不平 3挂接口径未对上")
    private Integer reasonType;

    /** 该动手那一日的止点时刻(头回定下即钉死,不许往后推来凑数) */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "该动手那一日的止点时刻")
    private Date dueAt;

    /** 提前开口那一刻=止点往前挪amount日(能挪的只有这一栏) */
    @TableField("open_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提前开口那一刻")
    private Date openAt;

    /** 提前几日开口派单(全单唯一可挪的一栏) */
    @TableField("amount")
    @ApiModelProperty(value = "提前几日开口派单")
    private BigDecimal amount;

    /** 事由与办法记要(照名录口径写) */
    @TableField("content")
    @ApiModelProperty(value = "事由与办法记要")
    private String content;

    /** 实际派出所属轮次号(同一回执行共用一笔) */
    @TableField("round_no")
    @ApiModelProperty(value = "实际派出所属轮次号")
    private String roundNo;

    /** 派出那一刻钉住的时刻(派成第二笔,落定后不许被后来盖掉) */
    @TableField("dispatch_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "派出那一刻钉住的时刻")
    private Date dispatchAt;

    /** 派不成缘故(部门撤并/承办栏整季空着/过期撤回等,随这行落着) */
    @TableField("fail_reason")
    @ApiModelProperty(value = "派不成缘故")
    private String failReason;

    /** 条目情形 0候派 1已派出 2派不成 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0候派 1已派出 2派不成")
    private Integer status;

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

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public Integer getReasonType() {
        return reasonType;
    }

    public void setReasonType(Integer reasonType) {
        this.reasonType = reasonType;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public Date getOpenAt() {
        return openAt;
    }

    public void setOpenAt(Date openAt) {
        this.openAt = openAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public Date getDispatchAt() {
        return dispatchAt;
    }

    public void setDispatchAt(Date dispatchAt) {
        this.dispatchAt = dispatchAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
