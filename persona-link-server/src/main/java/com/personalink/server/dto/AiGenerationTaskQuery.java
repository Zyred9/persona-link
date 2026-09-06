package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** AI 题库任务分页查询参数。 */
public class AiGenerationTaskQuery {
    private String keyword;
    @Min(1)
    @Max(5)
    private Integer taskStatus;
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long size = 10;

    public String getKeyword() { return this.keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getTaskStatus() { return this.taskStatus; }
    public void setTaskStatus(Integer taskStatus) { this.taskStatus = taskStatus; }
    public long getPage() { return this.page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return this.size; }
    public void setSize(long size) { this.size = size; }
}
