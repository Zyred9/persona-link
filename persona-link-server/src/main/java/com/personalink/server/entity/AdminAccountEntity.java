package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 后台账号实体，对应 t_admin_account。
 *
 * <p>密码字段只保存 BCrypt 哈希，不保存明文密码。</p>
 *
 * @author persona-link
 * @since 1.0.0
 */
@TableName("t_admin_account")
public class AdminAccountEntity {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录账号。 */
    private String username;
    /** BCrypt 密码哈希。 */
    private String passwordHash;
    /** 显示名称。 */
    private String displayName;
    /** 角色类型：1管理员，2内容运营，3只读查看。 */
    private Integer roleType;
    /** 状态：1启用，0禁用。 */
    private Integer status;
    /** 最后登录时间。 */
    private LocalDateTime lastLoginAt;
    /** 删除标记：0正常，1删除。 */
    @TableLogic
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

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getRoleType() {
        return this.roleType;
    }

    public void setRoleType(Integer roleType) {
        this.roleType = roleType;
    }

    public Integer getStatus() {
        return this.status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return this.lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
