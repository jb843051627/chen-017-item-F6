package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TItemImplementCardMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.model.auto.TItemImplementCard;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.service.ITItemImplementCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 实施清单配置卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TItemImplementCardServiceImpl extends ServiceImpl<TItemImplementCardMapper, TItemImplementCard> implements ITItemImplementCardService {

    @Autowired
    private TItemDeptArchMapper itemDeptArchMapper;

    @Override
    public TItemImplementCard selectTItemImplementCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TItemImplementCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TItemImplementCard> selectTItemImplementCardList(Wrapper<TItemImplementCard> queryWrapper) {
        QueryWrapper<TItemImplementCard> wrapper = new QueryWrapper<TItemImplementCard>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTItemImplementCard(TItemImplementCard record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getBillNo());
        TItemDeptArch refArch = itemDeptArchMapper.selectOne(new QueryWrapper<TItemDeptArch>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TItemImplementCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTItemImplementCard(TItemImplementCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TItemImplementCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TItemImplementCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int advanceTItemImplementCard(Long id) {
        if (id == null) {
            return 0;
        }
        TItemImplementCard cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer st = cur.getStatus();
        // 缺陷1：状态为空时直接 NPE（存量兼容缺失，坑6）
        // 缺陷2：跳态无守卫——待核对可直接推到任意后续态（不逐段校验，坑1）
        int next = st.intValue() + 1;
        // 缺陷3：推进不生成核验串（版本核验串缺失，坑4）
        UpdateWrapper<TItemImplementCard> uw = new UpdateWrapper<TItemImplementCard>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int revertTItemImplementCard(Long id) {
        if (id == null) {
            return 0;
        }
        TItemImplementCard cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer st = cur.getStatus();
        if (st == null) {
            return 0;
        }
        // 缺陷4：退回清掉核验串（不沉底留痕，坑5）；且不守卫可退回范围（待核对也能退，坑1）
        int next = st.intValue() - 1;
        UpdateWrapper<TItemImplementCard> uw = new UpdateWrapper<TItemImplementCard>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        uw.set("content", null);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int deleteTItemImplementCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTItemImplementCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
