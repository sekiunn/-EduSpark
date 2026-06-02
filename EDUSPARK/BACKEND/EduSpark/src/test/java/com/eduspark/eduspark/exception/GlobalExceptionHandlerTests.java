package com.eduspark.eduspark.exception;

import com.eduspark.eduspark.dto.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessExceptionShouldUseHttpStatusFromErrorCode() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.NOT_FOUND, "资源不存在")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("资源不存在");
    }

    @Test
    void handleGenericExceptionShouldReturn500() {
        ResponseEntity<Result<Void>> response = handler.handleException(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(500);
    }
}
