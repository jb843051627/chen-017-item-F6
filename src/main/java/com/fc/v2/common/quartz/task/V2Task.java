package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.CheckRoundResult;
import com.fc.v2.service.ITItemCheckTaskService;
import com.fc.v2.service.ITSysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 *测试类
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	@Autowired
    private ITSysUserService userService;

	@Autowired
	private ITItemCheckTaskService itemCheckTaskService;

    /**
     * 要素定期核查派单：系统照日子一轮一轮跑。
     * 先名录一键开单，再跑本轮；夜里/节假日段外自然压着，"本轮无事"与"跑不起来"各报各的。
     * 在 quartz 任务里配调用串：v2Task.checkDispatchRound()
     */
    public void checkDispatchRound()
    {
        itemCheckTaskService.generateFromCatalog();
        CheckRoundResult result = itemCheckTaskService.runRound(new Date());
        System.out.println("要素核查派单轮次 " + result.getRoundNo() + "：" + result.getMessage()
                + "（候派 " + result.getWaitingCount() + "／已派出 " + result.getDispatchedCount()
                + "／派不成 " + result.getFailedCount() + "）");
    }
    /**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }
}
