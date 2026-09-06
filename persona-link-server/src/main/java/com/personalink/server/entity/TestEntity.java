package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 测试题型实体，对应 t_test。 */
@TableName("t_test")
public class TestEntity extends BaseAssessmentEntity {
    /** 后台题型名称。 */
    private String testName;
    /** 测试类型：1单人，2双人。 */
    private Integer testType;
    /** 分类 ID。 */
    private Long categoryId;
    /** 状态：1启用，0停用。 */
    private Integer status;
    /** 首页展示位置：0普通列表，1焦点位，2推荐位。 */
    private Integer homeDisplay;
    /** 首页排序值，越大越靠前。 */
    private Integer homeSort;
    public String getTestName() { return this.testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public Integer getTestType() { return this.testType; }
    public void setTestType(Integer testType) { this.testType = testType; }
    public Long getCategoryId() { return this.categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getStatus() { return this.status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getHomeDisplay() { return this.homeDisplay; }
    public void setHomeDisplay(Integer homeDisplay) { this.homeDisplay = homeDisplay; }
    public Integer getHomeSort() { return this.homeSort; }
    public void setHomeSort(Integer homeSort) { this.homeSort = homeSort; }
}
