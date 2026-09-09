-- 手工建库脚本，由数据库管理员执行。

-- 用户反馈和协议配置增量；不写入占位协议正文。执行前核对目标数据库。
CREATE TABLE IF NOT EXISTS t_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '提交用户',
    request_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '用户内幂等请求号',
    content VARCHAR(500) NOT NULL COMMENT '反馈或举报纯文本',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_request (open_id, request_id),
    KEY idx_feedback_created (deleted, create_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';
CREATE TABLE IF NOT EXISTS t_legal_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    type TINYINT NOT NULL COMMENT '1用户协议，2隐私保护指引，3免责声明',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    content MEDIUMTEXT NOT NULL COMMENT '协议纯文本正文',
    version BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '发布版本',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协议配置';

CREATE TABLE t_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_name VARCHAR(64) NOT NULL COMMENT '分类名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category_status_sort (status, deleted, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题型分类';

CREATE TABLE t_test (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    test_name VARCHAR(100) NOT NULL COMMENT '后台内部题型名称',
    test_type TINYINT NOT NULL COMMENT '测试类型：1单人，2双人',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    home_display TINYINT NOT NULL DEFAULT 0 COMMENT '首页位置：0普通列表，1焦点位，2推荐位',
    home_sort INT NOT NULL DEFAULT 0 COMMENT '首页排序值，越大越靠前',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_test_category (category_id, status, deleted),
    KEY idx_test_home (home_display, status, deleted, home_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试题型';

CREATE TABLE t_test_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    test_id BIGINT UNSIGNED NOT NULL COMMENT '题型ID',
    version_no INT UNSIGNED NOT NULL COMMENT '版本号',
    title VARCHAR(100) NOT NULL COMMENT '版本标题快照',
    cover_url VARCHAR(500) NOT NULL COMMENT '版本封面快照',
    detail_image_url VARCHAR(500) NULL COMMENT '版本详情整图快照，与首页封面独立',
    description TEXT NULL COMMENT '题型说明',
    estimated_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '预计答题分钟数',
    draw_question_count SMALLINT UNSIGNED NOT NULL COMMENT '单次随机抽取题目数',
    version_status TINYINT NOT NULL DEFAULT 1 COMMENT '1草稿，2审核中，3待发布，4已发布，5已下线，6已归档',
    version_note VARCHAR(500) NULL COMMENT '版本说明',
    review_reason VARCHAR(500) NULL COMMENT '审核驳回原因',
    scheduled_at DATETIME NULL COMMENT '计划发布时间',
    published_at DATETIME NULL COMMENT '实际发布时间',
    offline_at DATETIME NULL COMMENT '下线时间',
    risk_offline_flag TINYINT NOT NULL DEFAULT 0 COMMENT '风险下线：0否，1是',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_version (test_id, version_no),
    KEY idx_version_publish (version_status, scheduled_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试题型版本';

CREATE TABLE t_score_dimension (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '题型版本ID',
    dimension_code VARCHAR(32) NOT NULL COMMENT '维度编码',
    dimension_name VARCHAR(64) NOT NULL COMMENT '维度名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '展示及同分判定顺序，越小越优先',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_dimension_code (version_id, dimension_code),
    KEY idx_dimension_version_sort (version_id, deleted, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题型计分维度';

CREATE TABLE t_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '题型版本ID',
    question_type TINYINT NOT NULL DEFAULT 1 COMMENT '题型：1单选，2多选',
    dimension_id BIGINT UNSIGNED NULL COMMENT '单选题计分维度ID；多选题由选项配置维度',
    min_select_count TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '最少可选数量',
    max_select_count TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '最多可选数量',
    question_no SMALLINT UNSIGNED NOT NULL COMMENT '题号',
    question_text VARCHAR(500) NOT NULL COMMENT '题干',
    required_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否必答：1是，0否',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_question_no (version_id, question_no),
    KEY idx_question_version_sort (version_id, deleted, sort_no),
    KEY idx_question_dimension (version_id, dimension_id, deleted),
    CONSTRAINT chk_question_select_range CHECK (min_select_count >= 1 AND min_select_count <= max_select_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版本题目';

CREATE TABLE t_question_option (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
    option_code VARCHAR(16) NOT NULL COMMENT '选项编码',
    option_text VARCHAR(300) NOT NULL COMMENT '选项文案',
    dimension_id BIGINT UNSIGNED NULL COMMENT '多选题选项计分维度ID；单选题为空',
    score_value SMALLINT NOT NULL COMMENT '选项整数计分值',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_option_code (question_id, option_code),
    KEY idx_option_question_sort (question_id, deleted, sort_no),
    KEY idx_option_dimension (dimension_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目选项';

CREATE TABLE t_result_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '题型版本ID',
    dimension_id BIGINT UNSIGNED NOT NULL COMMENT '匹配的主维度ID',
    result_code VARCHAR(32) NOT NULL COMMENT '结果编码',
    result_name VARCHAR(100) NOT NULL COMMENT '结果名称',
    score_min DECIMAL(5,2) NOT NULL COMMENT '标准分下限，包含',
    score_max DECIMAL(5,2) NOT NULL COMMENT '标准分上限，除100外不包含',
    basic_result_json JSON NOT NULL COMMENT '基础结果内容',
    deep_result_json JSON NULL COMMENT '深度结果内容',
    share_copy_json JSON NULL COMMENT '分享文案配置',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '匹配顺序，越小越先匹配',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_result_code (version_id, result_code),
    KEY idx_result_match (version_id, dimension_id, deleted, sort_no),
    CONSTRAINT chk_result_score_range CHECK (score_min >= 0 AND score_max <= 100 AND score_min < score_max)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结果模板';

CREATE TABLE t_answer_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    answer_no VARCHAR(32) NOT NULL COMMENT '答卷业务编号',
    create_request_id VARCHAR(64) NOT NULL COMMENT '创建答卷幂等请求号',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '微信用户OpenID',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '锁定的题型版本ID',
    answer_type TINYINT NOT NULL COMMENT '答卷类型：1单人，2双人参与者',
    answer_status TINYINT NOT NULL DEFAULT 1 COMMENT '1作答中，2已提交，3报告已生成，4已放弃',
    submit_request_id VARCHAR(64) NULL COMMENT '提交幂等请求号',
    submitted_at DATETIME NULL COMMENT '提交时间',
    report_ready_at DATETIME NULL COMMENT '报告生成时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_no (answer_no),
    UNIQUE KEY uk_answer_create_request (create_request_id),
    UNIQUE KEY uk_answer_submit_request (submit_request_id),
    KEY idx_answer_open_time (open_id, create_date),
    KEY idx_answer_version_status (version_id, answer_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答题会话';

CREATE TABLE t_answer_session_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    answer_session_id BIGINT UNSIGNED NOT NULL COMMENT '答题会话ID',
    question_id BIGINT UNSIGNED NOT NULL COMMENT '本次抽中的题目ID',
    sort_no SMALLINT UNSIGNED NOT NULL COMMENT '本次答题顺序',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_question (answer_session_id, question_id),
    UNIQUE KEY uk_session_question_sort (answer_session_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答题会话题目快照';

CREATE TABLE t_answer_detail (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    answer_session_id BIGINT UNSIGNED NOT NULL COMMENT '答题会话ID',
    question_id BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
    option_id BIGINT UNSIGNED NOT NULL COMMENT '选项ID',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_question_option (answer_session_id, question_id, option_id),
    KEY idx_answer_detail_question (answer_session_id, question_id),
    KEY idx_answer_detail_option (option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答卷明细';

CREATE TABLE t_report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    report_no VARCHAR(32) NOT NULL COMMENT '报告业务编号',
    answer_session_id BIGINT UNSIGNED NOT NULL COMMENT '答题会话ID',
    result_code VARCHAR(32) NOT NULL COMMENT '命中的结果编码',
    result_snapshot JSON NOT NULL COMMENT '生成时的结果展示快照',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_no (report_no),
    UNIQUE KEY uk_report_answer (answer_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单人测试报告';

CREATE TABLE t_pair_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    pair_no VARCHAR(32) NOT NULL COMMENT '配对业务编号',
    create_request_id VARCHAR(64) NOT NULL COMMENT '创建邀请幂等请求号',
    invite_token_hash CHAR(64) NOT NULL COMMENT '邀请令牌哈希，不保存明文',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '双方共同使用的题型版本ID',
    initiator_open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '发起者OpenID',
    initiator_answer_session_id BIGINT UNSIGNED NOT NULL COMMENT '发起者答题会话ID',
    partner_open_id VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '受邀者OpenID',
    partner_answer_session_id BIGINT UNSIGNED NULL COMMENT '受邀者答题会话ID',
    pair_status TINYINT NOT NULL DEFAULT 1 COMMENT '1发起者已完成，2对方已加入，3双方已完成，4报告已生成，5已过期，6已取消',
    initiator_visible_flag TINYINT NOT NULL DEFAULT 1 COMMENT '发起者记录可见：1是，0否',
    partner_visible_flag TINYINT NOT NULL DEFAULT 1 COMMENT '受邀者记录可见：1是，0否',
    joined_at DATETIME NULL COMMENT '受邀者加入时间',
    expires_at DATETIME NOT NULL COMMENT '邀请过期时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pair_no (pair_no),
    UNIQUE KEY uk_pair_create_request (create_request_id),
    UNIQUE KEY uk_pair_invite_token (invite_token_hash),
    UNIQUE KEY uk_pair_partner_answer (partner_answer_session_id),
    KEY idx_pair_initiator_time (initiator_open_id, create_date),
    KEY idx_pair_partner_time (partner_open_id, create_date),
    KEY idx_pair_status_expire (pair_status, expires_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='双人配对会话';

CREATE TABLE t_pair_report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    report_no VARCHAR(32) NOT NULL COMMENT '双人报告业务编号',
    pair_session_id BIGINT UNSIGNED NOT NULL COMMENT '配对会话ID',
    result_snapshot JSON NOT NULL COMMENT '双人聚合报告快照',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pair_report_no (report_no),
    UNIQUE KEY uk_pair_report_session (pair_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='双人聚合报告';

CREATE TABLE t_ai_generation_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no VARCHAR(32) NOT NULL COMMENT 'AI生成任务业务编号',
    request_id VARCHAR(64) NOT NULL COMMENT '创建请求幂等编号',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '生成内容所属题型版本ID',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '创建任务的后台操作人ID',
    model_name VARCHAR(64) NOT NULL COMMENT '实际使用的AI模型名称',
    prompt_text TEXT NOT NULL COMMENT '运营输入的测试类型提示词',
    target_question_count SMALLINT UNSIGNED NOT NULL COMMENT '目标题库题目数',
    current_batch_no SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前批次号，未开始为0',
    total_batch_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总批次数',
    completed_batch_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已完成批次数',
    generated_question_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已生成题目数',
    retry_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已重试次数',
    dimension_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '维度是否已生成：1是，0否',
    result_rule_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '结果规则是否已生成：1是，0否',
    task_status TINYINT NOT NULL DEFAULT 1 COMMENT '1待生成，2生成中，3待人工审核，4生成失败，5已提交',
    error_message VARCHAR(1000) NULL COMMENT '最近一次生成失败原因',
    completed_at DATETIME NULL COMMENT '生成完成时间',
    submitted_at DATETIME NULL COMMENT '提交题型库时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_task_no (task_no),
    UNIQUE KEY uk_ai_task_request (request_id),
    UNIQUE KEY uk_ai_task_version (version_id),
    KEY idx_ai_task_status_time (task_status, deleted, update_date),
    CONSTRAINT chk_ai_target_question_count CHECK (target_question_count > 0),
    CONSTRAINT chk_ai_batch_progress CHECK (
        current_batch_no <= total_batch_count AND completed_batch_count <= total_batch_count
    ),
    CONSTRAINT chk_ai_question_progress CHECK (generated_question_count <= target_question_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI题库生成任务';

CREATE TABLE t_content_audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type TINYINT NOT NULL COMMENT '业务类型：1题型，2题型版本，3首页配置',
    biz_id BIGINT UNSIGNED NOT NULL COMMENT '业务对象ID',
    action_type TINYINT NOT NULL COMMENT '操作类型，由业务枚举定义',
    before_snapshot JSON NULL COMMENT '操作前快照',
    after_snapshot JSON NULL COMMENT '操作后快照',
    reason VARCHAR(500) NULL COMMENT '操作原因或审核意见',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_audit_biz_time (biz_type, biz_id, create_date),
    KEY idx_audit_operator_time (operator_id, create_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容操作审计日志';

CREATE TABLE t_analytics_visitor (
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '微信用户OpenID',
    first_visit_at DATETIME NOT NULL COMMENT '首次访问时间',
    last_visit_at DATETIME NOT NULL COMMENT '最近访问时间',
    PRIMARY KEY (open_id),
    KEY idx_visitor_first_visit (first_visit_at),
    KEY idx_visitor_last_visit (last_visit_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信用户访问汇总';

CREATE TABLE t_analytics_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '客户端事件唯一ID，用于幂等去重',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '微信用户OpenID',
    session_id VARCHAR(64) NOT NULL COMMENT '小程序会话标识',
    event_type TINYINT NOT NULL COMMENT '1小程序打开，2页面浏览，3开始测试，4完成测试，5分享',
    page_path VARCHAR(255) NULL COMMENT '页面路径，不包含查询参数',
    business_id BIGINT UNSIGNED NULL COMMENT '关联题型或报告等业务ID',
    source_scene INT NULL COMMENT '微信小程序场景值',
    channel VARCHAR(32) NULL COMMENT '业务渠道',
    app_version VARCHAR(32) NULL COMMENT '小程序版本',
    client_time DATETIME NOT NULL COMMENT '客户端事件时间',
    received_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务端接收时间，统计以此字段为准',
    PRIMARY KEY (id),
    UNIQUE KEY uk_analytics_event_id (event_id),
    KEY idx_event_type_time (event_type, received_time),
    KEY idx_event_open_time (open_id, received_time),
    KEY idx_event_session_time (session_id, received_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小程序访问事件';

CREATE TABLE t_analytics_visitor_day (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    stat_date DATE NOT NULL COMMENT '统计日期',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '微信用户OpenID',
    first_visit_at DATETIME NOT NULL COMMENT '当日首次访问时间',
    last_visit_at DATETIME NOT NULL COMMENT '当日最近访问时间',
    pv_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日页面浏览次数',
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_day (stat_date, open_id),
    KEY idx_visitor_day_last_visit (stat_date, last_visit_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信用户访问日统计';

CREATE TABLE t_wechat_metric_day (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    stat_date DATE NOT NULL COMMENT '微信统计日期',
    session_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '打开次数',
    visit_pv INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '访问PV',
    visit_uv INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '访问UV',
    visit_uv_new INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '新增访问UV',
    stay_time_uv DECIMAL(10,2) NULL COMMENT '人均停留时长，单位秒',
    stay_time_session DECIMAL(10,2) NULL COMMENT '次均停留时长，单位秒',
    visit_depth DECIMAL(10,2) NULL COMMENT '平均访问深度',
    sync_status TINYINT NOT NULL DEFAULT 1 COMMENT '同步状态：1成功，0失败',
    synced_at DATETIME NOT NULL COMMENT '同步时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_metric_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信官方日访问数据';

CREATE TABLE t_admin_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    display_name VARCHAR(64) NOT NULL COMMENT '显示名称',
    role_type TINYINT NOT NULL DEFAULT 1 COMMENT '固定角色：1管理员，2内容运营，3只读查看',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    last_login_at DATETIME NULL COMMENT '最后登录时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username),
    KEY idx_admin_status (status, deleted),
    CONSTRAINT chk_admin_role CHECK (role_type BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台账号';

CREATE TABLE t_business_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_token_hash CHAR(64) NOT NULL COMMENT '业务会话令牌哈希，不保存明文',
    session_type TINYINT NOT NULL COMMENT '会话类型：1后台，2微信小程序',
    admin_account_id BIGINT UNSIGNED NULL COMMENT '后台账号ID',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '微信用户OpenID',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    last_access_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    revoked_at DATETIME NULL COMMENT '注销时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常，1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_session_token (session_token_hash),
    KEY idx_business_session_admin (admin_account_id, expires_at, deleted),
    KEY idx_business_session_open (open_id, expires_at, deleted),
    CONSTRAINT chk_business_session_subject CHECK (
        (session_type = 1 AND admin_account_id IS NOT NULL AND open_id IS NULL)
        OR (session_type = 2 AND admin_account_id IS NULL AND open_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持久化业务会话';

-- 已有库增量执行；不会修改题型图片或发布版本。
CREATE TABLE IF NOT EXISTS t_image_generation_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id CHAR(36) NOT NULL COMMENT '请求幂等 UUID',
    test_id BIGINT UNSIGNED NOT NULL COMMENT '关联题型',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '创建人',
    provider TINYINT NOT NULL COMMENT '1千问，2火山',
    model_name VARCHAR(128) NOT NULL COMMENT '模型快照',
    endpoint VARCHAR(500) NOT NULL COMMENT 'API地址快照，无密钥',
    region VARCHAR(64) NULL COMMENT '区域快照',
    prompt_text VARCHAR(1500) NOT NULL COMMENT '运营关键词',
    cover_prompt TEXT NOT NULL COMMENT '封面最终提示词',
    detail_prompt TEXT NOT NULL COMMENT '详情最终提示词',
    cover_width INT NOT NULL DEFAULT 800 COMMENT '封面最终宽度像素',
    cover_height INT NOT NULL DEFAULT 800 COMMENT '封面最终高度像素',
    detail_width INT NOT NULL DEFAULT 1100 COMMENT '详情最终宽度像素',
    detail_height INT NOT NULL DEFAULT 500 COMMENT '详情最终高度像素',
    cover_task_id VARCHAR(256) NULL COMMENT '封面上游任务编号',
    detail_task_id VARCHAR(256) NULL COMMENT '详情上游任务编号',
    cover_submission_started TINYINT NOT NULL DEFAULT 0 COMMENT '封面已发起提交',
    detail_submission_started TINYINT NOT NULL DEFAULT 0 COMMENT '详情已发起提交',
    cover_url VARCHAR(500) NULL COMMENT '转存封面图地址',
    detail_image_url VARCHAR(500) NULL COMMENT '转存详情图地址',
    task_status TINYINT NOT NULL DEFAULT 1 COMMENT '1排队2生成3成功4失败5已采用',
    error_message VARCHAR(1000) NULL COMMENT '失败原因',
    applied_version_id BIGINT UNSIGNED NULL COMMENT '采用后草稿版本',
    completed_at DATETIME NULL COMMENT '生成完成时间',
    applied_at DATETIME NULL COMMENT '采用时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常1删除',
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_test_id BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN deleted = 0 AND task_status IN (1, 2) THEN test_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_image_task_request (request_id),
    UNIQUE KEY uk_image_task_active_test (active_test_id),
    KEY idx_image_task_test (test_id, deleted, id),
    CONSTRAINT chk_image_task_provider CHECK (provider IN (1, 2)),
    CONSTRAINT chk_image_task_status CHECK (task_status BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI封面详情双图任务';
