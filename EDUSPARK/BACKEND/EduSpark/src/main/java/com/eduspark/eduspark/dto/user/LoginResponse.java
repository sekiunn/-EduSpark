package com.eduspark.eduspark.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * JWT访问令牌
     */
    private String accessToken;

    /**
     * 令牌类型（Bearer）
     */
    private String tokenType;

    /**
     * 过期时间（秒）
     */
    private Long expiresIn;

    /**
     * 用户信息
     */
    private UserInfoResponse user;
}
