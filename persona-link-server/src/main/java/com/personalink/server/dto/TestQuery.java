package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 题型分页查询参数。 */
public class TestQuery {
    private String keyword;
    private Integer testType;
    private Long categoryId;
    private Integer status;
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 20;

    public String getKeyword() { return this.keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getTestType() { return this.testType; }
    public void setTestType(Integer testType) { this.testType = testType; }
    public Long getCategoryId() { return this.categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getStatus() { return this.status; }
    public void setStatus(Integer status) { this.status = status; }
    public long getPage() { return this.page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return this.size; }
    public void setSize(long size) { this.size = size; }
}
