-- item 政务服务事项标准化管理与实施清单同源发布管理 -- schema (chen-017)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_017

CREATE TABLE IF NOT EXISTS t_item_alert_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '办件逾期风险判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '低界逾期率(百分比)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界逾期率(百分比)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界逾期率(百分比)',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '让线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='办件逾期风险判定线';

CREATE TABLE IF NOT EXISTS t_item_catalog_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '上级目录下发册次号',
  row_no int DEFAULT NULL COMMENT '原册行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被挑出的前置项代号(即事项码,派单直带)',
  site_no varchar(64) DEFAULT NULL COMMENT '承办部门底册代号(整季空着即此栏为NULL,派单照直带过去)',
  reason_type int DEFAULT NULL COMMENT '该轮核哪一桩 1要素同上位名录不合 2承诺时限同实际办件接不平 3挂接口径没对上',
  due_at datetime DEFAULT NULL COMMENT '该动手那一日的止点时刻(派单照直带过去,不许后挪)',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前几日开口(派单照直带过去,唯一可挪的一栏)',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行待核要素项数',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已挂接 2挂起',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上级目录下发册核对行';

CREATE TABLE IF NOT EXISTS t_item_catalog_ver (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '事项目录版次单号',
  stage int DEFAULT NULL COMMENT '当前档次 0..3（草拟/复核/付发布/已上柜）',
  status int DEFAULT NULL COMMENT '版次落点 0未起 1在办 2已作废',
  content varchar(255) DEFAULT NULL COMMENT '版次记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项目录条目版次单';

CREATE TABLE IF NOT EXISTS t_item_change_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '要素调整核签单号',
  node_no int DEFAULT NULL COMMENT '当前所在道次 0..2（县核/市核/省定）',
  sign_mode int DEFAULT NULL COMMENT '同口并签办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本口应落名数',
  sign_count int DEFAULT NULL COMMENT '本口已落名数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项要素调整逐级核签单';

CREATE TABLE IF NOT EXISTS t_item_check_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '核查派单号',
  source_no varchar(64) DEFAULT NULL COMMENT '重派来源旧派单号(撤回重派时旧单留底、新单另起一号)',
  item_code varchar(64) DEFAULT NULL COMMENT '事项码(由名录条目直带,不另请人抄第二遍)',
  site_no varchar(64) DEFAULT NULL COMMENT '被派的承办部门底册代号(整季空着即此栏为NULL)',
  site_name varchar(128) DEFAULT NULL COMMENT '被派的承办部门全称(开单时自底册带过来留底)',
  reason_type int DEFAULT NULL COMMENT '事由归类 1要素同上位名录不合 2承诺时限同实际办件接不平 3挂接口径没对上',
  due_at datetime DEFAULT NULL COMMENT '该动手那一日的止点时刻(头回定下即钉死,不许往后推来凑数)',
  open_at datetime DEFAULT NULL COMMENT '提前开口那一刻=止点往前挪amount日(能挪的只有这一栏)',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前几日开口派单(全单唯一可挪的一栏)',
  content varchar(255) DEFAULT NULL COMMENT '事由与办法记要(照名录口径写)',
  round_no varchar(32) DEFAULT NULL COMMENT '实际派出所属轮次号(同一回执行共用一笔)',
  dispatch_at datetime DEFAULT NULL COMMENT '派出那一刻钉住的时刻(派成第二笔,落定后不许被后来盖掉)',
  fail_reason varchar(255) DEFAULT NULL COMMENT '派不成缘故(部门撤并/承办栏整季空着/过期撤回等,随这行落着)',
  status int DEFAULT NULL COMMENT '条目情形 0候派 1已派出 2派不成',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_check_status_due (status, due_at),
  KEY idx_check_round (round_no),
  KEY idx_check_item (item_code, site_no, reason_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='要素定期核查派单';

-- 值守开口班表：法定节假日/调休休息日落 0（整日不作声）；寻常值守日落 1；
-- 岁末那半个月（12-16 至 12-31）落 2，另按本表开口时辰切，不走寻常那个切法。
-- 表里没有的日子按寻常值守日、开口 09:00–17:00 兜底；夜里（开口时辰段外）一律静默。
CREATE TABLE IF NOT EXISTS t_item_duty_window (
  id bigint NOT NULL COMMENT '主键',
  cal_date date NOT NULL COMMENT '班表所属日(自然日,一日一行)',
  day_kind int DEFAULT 1 COMMENT '当日班别 0整日静默(法定节假日) 1寻常值守开口 2岁末半月班表开口',
  open_from time DEFAULT '09:00:00' COMMENT '开口时辰起(段外即夜里,不作声)',
  open_to time DEFAULT '17:00:00' COMMENT '开口时辰止',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_duty_cal_date (cal_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核查派单值守开口班表';

CREATE TABLE IF NOT EXISTS t_item_dept_arch (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '承办部门底册代号',
  site_name varchar(128) DEFAULT NULL COMMENT '部门全称',
  site_type varchar(32) DEFAULT NULL COMMENT '机构类别',
  road_name varchar(128) DEFAULT NULL COMMENT '落在哪一级政务区划(省—市—县)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单卡年办件量低界(件/年)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界(件/年)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界(件/年)',
  status int DEFAULT NULL COMMENT '底册情形 0在编 1已撤并',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='承办部门底册';

CREATE TABLE IF NOT EXISTS t_item_implement_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '实施清单配置卡号',
  node_no int DEFAULT NULL COMMENT '本卡版次序',
  site_id int DEFAULT NULL COMMENT '所属承办部门底册',
  site_no varchar(64) DEFAULT NULL COMMENT '所属部门底册代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡申报年办件量(件/年)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本卡承诺时限(工作日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版次序走）',
  grade_level int DEFAULT NULL COMMENT '年办件量折到的服务监督层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已冻住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实施清单配置卡';

-- 初始档案数据（两条种子底册：一条在编、一条已撤并）
-- t_item_dept_arch 两条种子底册：id=0 在编、id=1 已撤并。
-- 单卡年办件量三线上限取 300/1500/6000 件。
INSERT IGNORE INTO t_item_dept_arch (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZW00', '青屏市承办部门底册（市政务服务和数据管理局，在编）', '行政许可', '青屏省—青屏市—青屏县', 300.00, 1500.00, 6000.00, 0, 0, 'seed', NOW()),
       (1, 'ZW01', '青屏市承办部门底册（市住房和城乡建设管理局，已撤并）', '行政确认', '青屏省—青屏市—青屏县', 300.00, 1500.00, 6000.00, 1, 0, 'seed', NOW());


-- 菜单与按钮权限（itemCheckTask）：目录/菜单/跑单三枚；高位ID避开既有种子。
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (9100170001, '要素定期核查派单', '季度核查派单台账', '/itemCheckTask/view', 0, 0, 'item:itemCheckTask:view', 1, 'layui-icon-template-1', 17, 0, 'seed', NOW()),
       (9100170002, '派单集合', '派单台账集合', '/itemCheckTask/list', 0, 9100170001, 'item:itemCheckTask:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (9100170003, '开单跑轮撤回重派', '名录一键开单/跑一轮/撤回重派', '/itemCheckTask/runRound', 0, 9100170001, 'item:itemCheckTask:run', 2, 'layui-icon-refresh-3', NULL, 0, 'seed', NOW());
