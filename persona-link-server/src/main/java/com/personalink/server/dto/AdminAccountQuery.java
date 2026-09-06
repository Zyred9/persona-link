package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 后台账号分页查询参数。 */
public class AdminAccountQuery {
    private String keyword;
    @Min(1)
    @Max(3)
    private Integer roleType;
    @Min(0)
    @Max(1)
    private Integer status;
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 20;

    public String getKeyword() { return this.keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getRoleType() { return this.roleType; }
    public void setRoleType(Integer roleType) { this.roleType = roleType; }
    public Integer getStatus() { return this.status; }
    public void setStatus(Integer status) { this.status = status; }
    public long getPage() { return this.page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return this.size; }
    public void setSize(long size) { this.size = size; }
}
