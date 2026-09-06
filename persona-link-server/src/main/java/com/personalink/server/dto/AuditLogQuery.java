package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 内容审计分页查询参数。 */
public class AuditLogQuery {
    private Integer bizType;
    private Long bizId;
    private Long operatorId;
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 20;

    public Integer getBizType() { return this.bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public Long getBizId() { return this.bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Long getOperatorId() { return this.operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public long getPage() { return this.page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return this.size; }
    public void setSize(long size) { this.size = size; }
}
