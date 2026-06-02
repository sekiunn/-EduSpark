package com.eduspark.eduspark.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {

    /**
     * 登录方式：password-密码登录 sms-验证码登录
     */
    @NotBlank(message = "登录方式不能为空")
    private String loginType;

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 密码（loginType=password时填写）
     */
    private String password;

    /**
     * 短信验证码（loginType=sms时填写）
     */
    private String smsCode;
}
