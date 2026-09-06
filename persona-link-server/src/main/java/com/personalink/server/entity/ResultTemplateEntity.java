package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/** 结果模板实体，对应 t_result_template。 */
@TableName("t_result_template")
public class ResultTemplateEntity extends BaseAssessmentEntity {
    /** 版本 ID。 */
    private Long versionId;
    /** 主维度 ID。 */
    private Long dimensionId;
    /** 结果编码。 */
    private String resultCode;
    /** 结果名称。 */
    private String resultName;
    /** 分数下限，包含。 */
    private BigDecimal scoreMin;
    /** 分数上限。 */
    private BigDecimal scoreMax;
    /** 基础结果 JSON。 */
    private String basicResultJson;
    /** 深度结果 JSON。 */
    private String deepResultJson;
    /** 分享文案 JSON。 */
    private String shareCopyJson;
    /** 匹配顺序。 */
    private Integer sortNo;
    public Long getVersionId() { return this.versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getDimensionId() { return this.dimensionId; }
    public void setDimensionId(Long dimensionId) { this.dimensionId = dimensionId; }
    public String getResultCode() { return this.resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResultName() { return this.resultName; }
    public void setResultName(String resultName) { this.resultName = resultName; }
    public BigDecimal getScoreMin() { return this.scoreMin; }
    public void setScoreMin(BigDecimal scoreMin) { this.scoreMin = scoreMin; }
    public BigDecimal getScoreMax() { return this.scoreMax; }
    public void setScoreMax(BigDecimal scoreMax) { this.scoreMax = scoreMax; }
    public String getBasicResultJson() { return this.basicResultJson; }
    public void setBasicResultJson(String basicResultJson) { this.basicResultJson = basicResultJson; }
    public String getDeepResultJson() { return this.deepResultJson; }
    public void setDeepResultJson(String deepResultJson) { this.deepResultJson = deepResultJson; }
    public String getShareCopyJson() { return this.shareCopyJson; }
    public void setShareCopyJson(String shareCopyJson) { this.shareCopyJson = shareCopyJson; }
    public Integer getSortNo() { return this.sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
