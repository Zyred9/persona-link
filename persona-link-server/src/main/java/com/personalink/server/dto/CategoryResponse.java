package com.personalink.server.dto;

import java.time.LocalDateTime;

/**
 * 分类响应。
 */
public class CategoryResponse {

    private Long id;
    private String categoryName;
    private Long testCount;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime updateDate;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getTestCount() {
        return this.testCount;
    }

    public void setTestCount(Long testCount) {
        this.testCount = testCount;
    }

    public Integer getStatus() {
        return this.status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return this.sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDateTime getUpdateDate() {
        return this.updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }
}
