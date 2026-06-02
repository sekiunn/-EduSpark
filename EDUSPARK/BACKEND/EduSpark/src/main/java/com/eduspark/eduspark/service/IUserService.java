package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.user.*;

/**
 * 用户服务接口
 */
public interface IUserService {

    // ==================== 认证相关 ====================

    /**
     * 发送短信验证码
     */
    void sendSmsCode(SendSmsCodeRequest request);

    /**
     * 用户注册
     */
    LoginResponse register(UserRegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(UserLoginRequest request);

    /**
     * 忘记密码
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * 修改密码
     */
    void changePassword(ChangePasswordRequest request, Long userId);

    // ==================== 用户信息 ====================

    /**
     * 获取当前用户信息
     */
    UserInfoResponse getCurrentUser(Long userId);

    /**
     * 更新用户信息
     */
    void updateUserInfo(Long userId, UpdateUserInfoRequest request);

    /**
     * 更新用户头像
     */
    void updateAvatar(Long userId, String avatarUrl);

    // ==================== 工具方法 ====================

    /**
     * 验证短信验证码
     */
    boolean verifySmsCode(String phone, String code, String scene);

    /**
     * 获取用户ID（通过手机号）
     */
    Long getUserIdByPhone(String phone);
}
