package com.personalink.server.aspect;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerLogAspectTest {

    private static final String MASKED_VALUE = "***";

    @Test
    void logControllerShouldMaskSensitiveRequestAndResponseValues() throws Throwable {
        ControllerLogAspect aspect = new ControllerLogAspect(new ObjectMapper());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                Map.of("accessToken", "response-secret", "status", "ok"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(ControllerLogAspectTest.class);
        when(signature.getName()).thenReturn("login");
        when(signature.getParameterNames()).thenReturn(new String[]{"authorization", "request", "file"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "Bearer request-secret",
                Map.of("code", "wechat-code", "password", "login-password", "name", "tester"),
                new MockMultipartFile("file", "cover.png", "image/png", "binary-content".getBytes())});
        when(joinPoint.proceed()).thenReturn(response);
        Logger logger = (Logger) LoggerFactory.getLogger(ControllerLogAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/miniapp/auth/wechat");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        try {
            assertEquals(response, aspect.logController(joinPoint));
        } finally {
            RequestContextHolder.resetRequestAttributes();
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertTrue(logs.contains("请求路径：/api/miniapp/auth/wechat"));
        assertTrue(logs.contains("方式：POST"));
        assertTrue(logs.contains("入参："));
        assertTrue(logs.contains("出参："));
        assertTrue(logs.contains("cover.png"));
        assertTrue(logs.contains("tester"));
        assertTrue(logs.contains(MASKED_VALUE));
        assertFalse(logs.contains("request-secret"));
        assertFalse(logs.contains("wechat-code"));
        assertFalse(logs.contains("login-password"));
        assertFalse(logs.contains("response-secret"));
        assertFalse(logs.contains("binary-content"));
    }

}
