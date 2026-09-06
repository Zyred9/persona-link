package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 题型分类实体，对应 t_category。
 *
 * <p>排序值越大展示越靠前。</p>
 *
 * @author persona-link
 * @since 1.0.0
 */
@TableName("t_category")
public class CategoryEntity {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 分类名称。 */
    private String categoryName;
    /** 排序值，越大越靠前。 */
    private Integer sortNo;
    /** 状态：1启用，0禁用。 */
    private Integer status;
    /** 删除标记：0正常，1删除。 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    /** 创建时间。 */
    private LocalDateTime createDate;
    /** 更新时间。 */
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

    public Integer getSortNo() {
        return this.sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getStatus() {
        return this.status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDeleted() {
        return this.deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreateDate() {
        return this.createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public LocalDateTime getUpdateDate() {
        return this.updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }
}
