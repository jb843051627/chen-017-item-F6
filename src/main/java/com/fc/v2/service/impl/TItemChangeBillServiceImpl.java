package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TItemChangeBillMapper;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.service.ITItemChangeBillService;

/**
 * 事项要素调整逐级核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemChangeBillServiceImpl implements ITItemChangeBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TItemChangeBillMapper itemChangeBillMapper;

    @Override
    public TItemChangeBill selectTItemChangeBillById(Long id) {
        return this.itemChangeBillMapper.selectById(id);
    }

    @Override
    public TItemChangeBill approve(Long id, String approver, String comment) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        r.setNodeNo(Integer.valueOf((r.getNodeNo() == null ? 0 : r.getNodeNo()) + 1));
        r.setStatus(Integer.valueOf(r.getNodeNo() >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING));
        this.itemChangeBillMapper.updateById(r);
        return r;
    }

    @Override
    public TItemChangeBill reject(Long id, String approver, String comment) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        this.itemChangeBillMapper.updateById(r);
        return r;
    }

    @Override
    public TItemChangeBill rollback(Long id, String comment) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        this.itemChangeBillMapper.updateById(r);
        return r;
    }
}
