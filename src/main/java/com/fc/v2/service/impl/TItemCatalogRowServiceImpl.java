package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.service.ITItemCatalogRowService;

/**
 * 上级目录下发册核对行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemCatalogRowServiceImpl implements ITItemCatalogRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;

    @Override
    public TItemCatalogRow selectTItemCatalogRowById(Long id) {
        return this.itemCatalogRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TItemCatalogRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TItemCatalogRow> errors = new java.util.ArrayList<TItemCatalogRow>();
        int seq = 0;
        for (TItemCatalogRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.itemCatalogRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TItemCatalogRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.itemCatalogRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TItemCatalogRow> listErrors(String batchNo) {
        return this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
