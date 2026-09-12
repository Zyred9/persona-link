package com.personalink.server.exception;

import com.personalink.server.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void shouldConvertBusinessExceptionToUnifiedResponse() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleBusinessException(new BusinessException(40001, "题型不存在"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(40001, response.getBody().code());
        assertEquals("题型不存在", response.getBody().message());
    }

    @Test
    void shouldPreserveBusinessExceptionHttpStatus() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleBusinessException(new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "登录已失效"));

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(40101, response.getBody().code());
    }

    @Test
    void shouldReturnBadRequestForMalformedBodyAndTypeMismatch() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiResponse<Void>> malformedBody = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])));
        ResponseEntity<ApiResponse<Void>> typeMismatch = handler.handleMethodArgumentTypeMismatchException(
                new MethodArgumentTypeMismatchException("bad", Integer.class, "page", null,
                        new NumberFormatException()));

        assertEquals(400, malformedBody.getStatusCode().value());
        assertNotNull(malformedBody.getBody());
        assertEquals("请求体格式错误", malformedBody.getBody().message());
        assertEquals(400, typeMismatch.getStatusCode().value());
        assertNotNull(typeMismatch.getBody());
        assertEquals("page: 参数类型错误", typeMismatch.getBody().message());
    }

    @Test
    void shouldReturnNotFoundForMissingStaticResource() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleNoResourceFoundException(new NoResourceFoundException(HttpMethod.GET, "/favicon.ico"));

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().code());
        assertEquals("资源不存在", response.getBody().message());
    }
}
