package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TItemImplementCard;

import java.util.List;

/**
 * 实施清单配置卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITItemImplementCardService {

    /** 按主键查询 */
    TItemImplementCard selectTItemImplementCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TItemImplementCard> selectTItemImplementCardList(Wrapper<TItemImplementCard> queryWrapper);

    /** 新增 */
    int insertTItemImplementCard(TItemImplementCard record);

    /** 修改 */
    int updateTItemImplementCard(TItemImplementCard record);

    /** 批量删除 */
    int deleteTItemImplementCardByIds(String ids);

    /** 推进一态（待核对→已核对→已冻住） */
    int advanceTItemImplementCard(Long id);

    /** 退回一态（已核对→待核对；旧记录沉底） */
    int revertTItemImplementCard(Long id);

    /** 按主键删除 */
    int deleteTItemImplementCardById(Long id);
}
