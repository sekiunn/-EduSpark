package com.eduspark.eduspark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduspark.eduspark.config.JwtUtil;
import com.eduspark.eduspark.dto.user.*;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.SysUserMapper;
import com.eduspark.eduspark.pojo.entity.SysUser;
import com.eduspark.eduspark.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    // ==================== 依赖注入 ====================

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    // ==================== 构造器 ====================

    public UserServiceImpl(
            SysUserMapper userMapper,
            StringRedisTemplate redisTemplate,
            JwtUtil jwtUtil,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== 验证码相关 ====================

    /**
     * 验证码Redis Key前缀
     */
    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final long SMS_CODE_EXPIRE = 5; // 5分钟

    /**
     * 发送频率限制（内存中记录，用于开发环境）
     * 生产环境建议使用Redis记录
     */
    private final Map<String, Long> sendTimeCache = new ConcurrentHashMap<>();
    private static final long SEND_INTERVAL = 60 * 1000L; // 60秒内不能重复发送

    @Override
    public void sendSmsCode(SendSmsCodeRequest request) {
        String phone = request.getPhone();
        String scene = request.getScene();

        // 1. 频率检查
        Long lastSendTime = sendTimeCache.get(phone);
        if (lastSendTime != null
                && System.currentTimeMillis() - lastSendTime < SEND_INTERVAL) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "发送太频繁，请60秒后再试");
        }

        // 2. 场景校验
        if ("register".equals(scene)) {
            // 注册时手机号不能已存在
            SysUser existUser = userMapper.selectByPhone(phone);
            if (existUser != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该手机号已注册");
            }
        } else if ("login".equals(scene) || "verify".equals(scene)) {
            // 登录/验证时手机号必须存在
            SysUser existUser = userMapper.selectByPhone(phone);
            if (existUser == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该手机号未注册");
            }
        }

        // 3. 生成6位数字验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 4. 存储到Redis
        String redisKey = SMS_CODE_PREFIX + phone + ":" + scene;
        redisTemplate.opsForValue().set(redisKey, code, SMS_CODE_EXPIRE, TimeUnit.MINUTES);

        // 5. 更新发送时间
        sendTimeCache.put(phone, System.currentTimeMillis());

        // 6. 打印验证码到控制台（开发环境）
        // 生产环境应调用真实短信API（如阿里云、腾讯云）
        log.info("═══════════════════════════════════════════");
        log.info("  【短信验证码】手机号: {}  场景: {}", phone, scene);
        log.info("  【短信验证码】验证码: {}", code);
        log.info("  【短信验证码】有效期: {}分钟", SMS_CODE_EXPIRE);
        log.info("═══════════════════════════════════════════");
    }

    @Override
    public boolean verifySmsCode(String phone, String code, String scene) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String redisKey = SMS_CODE_PREFIX + phone + ":" + scene;
        String cachedCode = redisTemplate.opsForValue().get(redisKey);
        if (cachedCode == null) {
            return false;
        }
        // 验证码匹配后删除（一次性）
        if (cachedCode.equals(code.trim())) {
            redisTemplate.delete(redisKey);
            return true;
        }
        return false;
    }

    // ==================== 注册登录 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(UserRegisterRequest request) {
        // 1. 验证密码一致性
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码输入不一致");
        }

        // 2. 验证短信验证码
        if (!verifySmsCode(request.getPhone(), request.getSmsCode(), "register")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
        }

        // 3. 检查手机号是否已被注册（双重检查）
        SysUser existUser = userMapper.selectByPhone(request.getPhone());
        if (existUser != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该手机号已注册");
        }

        // 4. 创建用户（ID由数据库自增生成）
        SysUser user = SysUser.builder()
                .phone(request.getPhone())
                .username(request.getUsername() != null
                        ? request.getUsername()
                        : "用户" + request.getPhone().substring(7))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(SysUser.Role.TEACHER) // 默认教师角色
                .status(SysUser.Status.ENABLED)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(0)
                .build();

        userMapper.insert(user);
        log.info("用户注册成功: userId={}, phone={}", user.getId(), user.getPhone());

        // 5. 生成Token并返回
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse login(UserLoginRequest request) {
        // 1. 查询用户
        SysUser user = userMapper.selectByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号或密码错误");
        }

        // 2. 检查账号状态
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 3. 根据登录方式验证
        if ("password".equals(request.getLoginType())) {
            // 密码登录
            if (!StringUtils.hasText(request.getPassword())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "请输入密码");
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号或密码错误");
            }
        } else if ("sms".equals(request.getLoginType())) {
            // 验证码登录
            if (!verifySmsCode(request.getPhone(), request.getSmsCode(), "login")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
            }
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的登录方式");
        }

        // 4. 更新最后登录信息
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: userId={}, phone={}", user.getId(), user.getPhone());

        // 5. 生成Token并返回
        return buildLoginResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forgotPassword(ForgotPasswordRequest request) {
        // 1. 验证密码一致性
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码输入不一致");
        }

        // 2. 验证短信验证码
        if (!verifySmsCode(request.getPhone(), request.getSmsCode(), "verify")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
        }

        // 3. 查询用户
        SysUser user = userMapper.selectByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该手机号未注册");
        }

        // 4. 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("密码重置成功: userId={}, phone={}", user.getId(), user.getPhone());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request, Long userId) {
        // 1. 查询用户
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 2. 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "旧密码错误");
        }

        // 3. 验证新密码一致性
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码输入不一致");
        }

        // 4. 检查新旧密码是否相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码不能与旧密码相同");
        }

        // 5. 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("密码修改成功: userId={}", userId);
    }

    // ==================== 用户信息 ====================

    @Override
    public UserInfoResponse getCurrentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toUserInfoResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 选择性更新非空字段
        if (StringUtils.hasText(request.getUsername())) {
            user.setUsername(request.getUsername());
        }
        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getBio())) {
            user.setBio(request.getBio());
        }
        if (StringUtils.hasText(request.getAvatar())) {
            user.setAvatar(request.getAvatar());
        }
        user.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(user);
        log.info("用户信息更新: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(Long userId, String avatarUrl) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setAvatar(avatarUrl);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户头像更新: userId={}, avatarUrl={}", userId, avatarUrl);
    }

    @Override
    public Long getUserIdByPhone(String phone) {
        SysUser user = userMapper.selectByPhone(phone);
        return user != null ? user.getId() : null;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 构建登录响应
     */
    private LoginResponse buildLoginResponse(SysUser user) {
        String token = jwtUtil.generateToken(user.getId(), user.getPhone(), user.getRole());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpiration())
                .user(toUserInfoResponse(user))
                .build();
    }

    /**
     * 转换为用户信息响应
     */
    private UserInfoResponse toUserInfoResponse(SysUser user) {
        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .phone(maskPhone(user.getPhone()))
                .email(maskEmail(user.getEmail()))
                .avatar(user.getAvatar())
                .role(user.getRole())
                .bio(user.getBio())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 邮箱脱敏
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) return email;
        return parts[0].charAt(0) + "***" + parts[0].charAt(parts[0].length() - 1) + "@" + parts[1];
    }

    // ==================== 定时清理 ====================

    /**
     * 每小时清理过期发送记录
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanExpiredSendRecords() {
        long now = System.currentTimeMillis();
        sendTimeCache.entrySet().removeIf(entry ->
                now - entry.getValue() > SEND_INTERVAL * 2
        );
    }
}
