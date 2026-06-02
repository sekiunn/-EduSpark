package com.eduspark.eduspark.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送验证码请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendSmsCodeRequest {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 场景：register-注册 login-登录找回密码 verify-验证身份
     */
    @NotBlank(message = "场景不能为空")
    private String scene;
}
