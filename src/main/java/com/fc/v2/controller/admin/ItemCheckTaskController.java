package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TItemCheckTask;
import com.fc.v2.model.custom.CheckRoundResult;
import com.fc.v2.service.ITItemCheckTaskService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

/**
 * 要素定期核查派单 Controller（scheduling-job 形状：页面只翻台账、触跑轮次，不增不减条目）。
 *
 * 哪几桩这一轮该出手、当轮出去几条压住几条，一律由服务层这一回执行算定再落库；
 * 页面加不上一条也减不上一条，成没成只看这一回给回什么。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "要素定期核查派单")
@Controller
@RequestMapping("/itemCheckTask")
public class ItemCheckTaskController extends BaseController {

    private final String prefix = "admin/itemCheckTask";

    @Autowired
    private ITItemCheckTaskService itemCheckTaskService;

    @ApiOperation(value = "派单台账跳转", notes = "派单台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("item:itemCheckTask:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "要素定期核查派单台账", action = "list")
    @ApiOperation(value = "台账查询(只读)", notes = "台账查询(只读)")
    @GetMapping("/list")
    @RequiresPermissions("item:itemCheckTask:list")
    @ResponseBody
    public ResultTable list(TItemCheckTask record) {
        QueryWrapper<TItemCheckTask> queryWrapper = new QueryWrapper<TItemCheckTask>()
                .eq("del_flag", 0)
                .eq(record.getStatus() != null, "status", record.getStatus())
                .eq(record.getItemCode() != null && !record.getItemCode().trim().isEmpty(),
                        "item_code", record.getItemCode() == null ? null : record.getItemCode().trim())
                .orderByDesc("create_time");
        startPage();
        com.github.pagehelper.PageInfo<TItemCheckTask> page =
                new com.github.pagehelper.PageInfo<TItemCheckTask>(
                        itemCheckTaskService.selectTItemCheckTaskList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "名录照口径一键开单", action = "generate")
    @ApiOperation(value = "名录一键开单", notes = "事项码/承办部门/止点从名录条目直带")
    @PostMapping("/generate")
    @RequiresPermissions("item:itemCheckTask:run")
    @ResponseBody
    public AjaxResult generate() {
        int opened = itemCheckTaskService.generateFromCatalog().size();
        return AjaxResult.success("名录开单完成，新开口候派单" + opened + "条（撤并/承办空着已就地转派不成）");
    }

    @Log(title = "要素定期核查跑一轮", action = "runRound")
    @ApiOperation(value = "跑一轮", notes = "服务层算定再落库，回本轮三笔局面")
    @PostMapping("/runRound")
    @RequiresPermissions("item:itemCheckTask:run")
    @ResponseBody
    public AjaxResult runRound() {
        CheckRoundResult result = itemCheckTaskService.runRound(new Date());
        return AjaxResult.success(200, result.getMessage(), result);
    }

    @Log(title = "核查派单撤回重派", action = "reissue")
    @ApiOperation(value = "撤回重派", notes = "旧单留底、新单另起一号")
    @PostMapping("/reissue")
    @RequiresPermissions("item:itemCheckTask:run")
    @ResponseBody
    public AjaxResult reissue(Long id) {
        return itemCheckTaskService.reissue(id) != null
                ? AjaxResult.success("撤回重派完成，旧单留底、新单另起一号")
                : AjaxResult.error("原单不存在，撤回重派不出");
    }
}
