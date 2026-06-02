package com.eduspark.eduspark.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户信息请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserInfoRequest {

    /**
     * 用户名（昵称）
     */
    @Size(max = 50, message = "用户名最多50字符")
    private String username;

    /**
     * 邮箱
     */
    @Size(max = 100, message = "邮箱最多100字符")
    private String email;

    /**
     * 个人简介
     */
    @Size(max = 500, message = "简介最多500字符")
    private String bio;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL最多500字符")
    private String avatar;
}
