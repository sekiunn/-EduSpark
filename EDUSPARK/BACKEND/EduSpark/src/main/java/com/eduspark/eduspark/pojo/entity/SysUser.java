package com.eduspark.eduspark.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（昵称）
     */
    private String username;

    /**
     * 手机号（登录账号，唯一）
     */
    private String phone;

    /**
     * 邮箱（可选，用于找回密码）
     */
    private String email;

    /**
     * 密码（BCrypt加密存储）
     */
    private String password;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户角色：student / teacher / admin
     */
    private String role;

    /**
     * 账号状态：0-禁用 1-启用
     */
    private Integer status;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 扩展属性（JSON）
     */
    private Map<String, Object> metadata;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer isDeleted;

    // ==================== 枚举常量 ====================

    /**
     * 角色枚举
     */
    public static final class Role {
        public static final String STUDENT = "student";
        public static final String TEACHER = "teacher";
        public static final String ADMIN = "admin";

        private Role() {}
    }

    /**
     * 状态枚举
     */
    public static final class Status {
        public static final int DISABLED = 0;
        public static final int ENABLED = 1;

        private Status() {}
    }

    /**
     * 是否为教师
     */
    public boolean isTeacher() {
        return Role.TEACHER.equals(this.role);
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return this.status != null && this.status == Status.ENABLED;
    }
}