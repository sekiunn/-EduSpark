package com.eduspark.eduspark.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
    }

    @Test
    void doFilterShouldReturn401WhenTokenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/chat/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("\"code\":401");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterShouldPassThroughWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/chat/sessions");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.getUserId("valid-token")).thenReturn(7L);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute("userId")).isEqualTo(7L);
        assertThat(request.getAttribute("token")).isEqualTo("valid-token");
        verify(filterChain).doFilter(request, response);
    }
}
