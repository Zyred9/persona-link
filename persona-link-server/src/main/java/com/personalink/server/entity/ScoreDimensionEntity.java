package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 计分维度实体，对应 t_score_dimension。 */
@TableName("t_score_dimension")
public class ScoreDimensionEntity extends BaseAssessmentEntity {
    /** 版本 ID。 */
    private Long versionId;
    /** 维度编码。 */
    private String dimensionCode;
    /** 维度名称。 */
    private String dimensionName;
    /** 排序值，越小越优先。 */
    private Integer sortNo;
    public Long getVersionId() { return this.versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getDimensionCode() { return this.dimensionCode; }
    public void setDimensionCode(String dimensionCode) { this.dimensionCode = dimensionCode; }
    public String getDimensionName() { return this.dimensionName; }
    public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }
    public Integer getSortNo() { return this.sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
