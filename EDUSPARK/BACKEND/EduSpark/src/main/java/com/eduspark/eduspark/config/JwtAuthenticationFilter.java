package com.eduspark.eduspark.config;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.pojo.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 需要认证的路径
     */
    private static final String[] EXCLUDE_PATHS = {
            "/v1/user/sendSms",
            "/v1/user/register",
            "/v1/user/login",
            "/v1/user/forgotPassword",
            "/v1/knowledge/health",
            "/v1/chat/health"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 放行公开接口
        for (String exclude : EXCLUDE_PATHS) {
            if (path.contains(exclude)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 提取Token
        String token = extractToken(request);

        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("未授权访问: {} {}", request.getMethod(), path);
            sendUnauthorizedResponse(response, "未授权，请先登录");
            return;
        }

        Long userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);

        if (isAdminPath(path) && !SysUser.Role.ADMIN.equals(role)) {
            log.warn("无权限访问管理接口: method={}, path={}, userId={}, role={}",
                    request.getMethod(), path, userId, role);
            sendForbiddenResponse(response, "无权限访问管理端接口");
            return;
        }

        // 验证通过，设置用户信息到请求属性
        if (userId != null) {
            request.setAttribute("userId", userId);
            request.setAttribute("token", token);
            request.setAttribute("role", role);
            log.debug("用户认证成功: userId={}, role={}, path={}", userId, role, path);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminPath(String path) {
        return path != null && path.contains("/admin/");
    }

    /**
     * 从请求头提取Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 发送未授权响应
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("WWW-Authenticate", "Bearer");
        Result<?> result = Result.unauthorized(message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        Result<?> result = Result.forbidden(message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
