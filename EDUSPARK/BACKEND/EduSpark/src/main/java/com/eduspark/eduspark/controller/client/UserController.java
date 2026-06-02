package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.user.*;
import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.service.IUserService;
import com.eduspark.eduspark.service.IFileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequestMapping("/v1/user")
public class UserController {

    private final IUserService userService;
    private final IFileStorageService fileStorageService;

    public UserController(IUserService userService, IFileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    // ==================== 认证接口 ====================

    /**
     * 发送短信验证码
     */
    @PostMapping("/sendSms")
    public Result<Void> sendSms(@RequestBody @Valid SendSmsCodeRequest request) {
        log.info("收到发送验证码请求: phone={}, scene={}",
                maskPhone(request.getPhone()), request.getScene());
        userService.sendSmsCode(request);
        return Result.successMessage("验证码已发送");
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid UserRegisterRequest request) {
        log.info("收到注册请求: phone={}", maskPhone(request.getPhone()));
        LoginResponse response = userService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid UserLoginRequest request) {
        log.info("收到登录请求: phone={}, loginType={}",
                maskPhone(request.getPhone()), request.getLoginType());
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 忘记密码
     */
    @PostMapping("/forgotPassword")
    public Result<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        log.info("收到忘记密码请求: phone={}", maskPhone(request.getPhone()));
        userService.forgotPassword(request);
        return Result.successMessage("密码重置成功");
    }

    // ==================== 用户信息接口（需认证） ====================

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public Result<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.debug("获取用户信息: userId={}", userId);
        UserInfoResponse user = userService.getCurrentUser(userId);
        return Result.success(user);
    }

    /**
     * 修改密码
     */
    @PostMapping("/changePassword")
    public Result<Void> changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("收到修改密码请求: userId={}", userId);
        userService.changePassword(request, userId);
        return Result.successMessage("密码修改成功");
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    public Result<Void> updateUserInfo(
            @RequestBody @Valid UpdateUserInfoRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.debug("更新用户信息: userId={}", userId);
        userService.updateUserInfo(userId, request);
        return Result.successMessage("用户信息更新成功");
    }

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("上传头像: userId={}, fileName={}", userId, file.getOriginalFilename());

        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("只能上传图片文件");
        }

        try {
            byte[] bytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }
            String fileName = "avatar_" + userId + extension;
            String filePath = fileStorageService.upload(fileName, bytes, userId);
            String avatarUrl = fileStorageService.getUrl(filePath);

            userService.updateAvatar(userId, avatarUrl);

            log.info("头像上传成功: userId={}, url={}", userId, avatarUrl);
            return Result.success("头像上传成功", avatarUrl);

        } catch (Exception e) {
            log.error("头像上传失败: userId={}", userId, e);
            return Result.fail("头像上传失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
