package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TItemImplementCard;
import com.fc.v2.service.ITItemImplementCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 实施清单配置卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "实施清单配置卡")
@Controller
@RequestMapping("/ItemImplementCardController")
public class ItemImplementCardController extends BaseController {

    private final String prefix = "admin/itemImplementCard";

    @Autowired
    private ITItemImplementCardService itemImplementCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("item:itemImplementCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "实施清单配置卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("item:itemImplementCard:list")
    @ResponseBody
    public ResultTable list(TItemImplementCard record) {
        QueryWrapper<TItemImplementCard> queryWrapper = new QueryWrapper<TItemImplementCard>();
        startPage();
        com.github.pagehelper.PageInfo<TItemImplementCard> page =
                new com.github.pagehelper.PageInfo<TItemImplementCard>(itemImplementCardService.selectTItemImplementCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "实施清单配置卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("item:itemImplementCard:add")
    @ResponseBody
    public AjaxResult add(TItemImplementCard record) {
        return toAjax(itemImplementCardService.insertTItemImplementCard(record));
    }

    @Log(title = "实施清单配置卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("item:itemImplementCard:edit")
    @ResponseBody
    public AjaxResult editSave(TItemImplementCard record) {
        return toAjax(itemImplementCardService.updateTItemImplementCard(record));
    }

    @Log(title = "实施清单配置卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("item:itemImplementCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(itemImplementCardService.deleteTItemImplementCardByIds(ids));
    }
}
