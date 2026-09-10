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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

class ControllerLogAspectTest {

    @Test
    void assessmentAndReportBodiesAppearInSuccessAndFailureLogs() throws Throwable {
        var aspect = new ControllerLogAspect(new ObjectMapper());
        var point = mock(ProceedingJoinPoint.class);
        var signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"request"});
        when(point.getArgs()).thenReturn(new Object[]{Map.of("optionIds", "private-choice",
                "unrecognizedFutureField", "private-payload")});
        var response = ApiResponse.success(Map.of("resultSnapshot", "private-report",
                "selectedOptionIds", "private-choice"));
        var failure = new IllegalStateException("operation failed");
        Logger logger = (Logger) LoggerFactory.getLogger(ControllerLogAspect.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (String path : new String[]{"/api/miniapp/assessments/1/answers",
                    "/api/miniapp/reports/1", "/api/miniapp/pairs/1/report"}) {
                var request = new MockHttpServletRequest("POST", "/persona" + path);
                request.setContextPath("/persona");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                doReturn(response).doThrow(failure).when(point).proceed();
                assertSame(response, aspect.logController(point));
                assertSame(failure, assertThrows(IllegalStateException.class, () -> aspect.logController(point)));
            }
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);
            assertTrue(logs.contains("private-choice"));
            assertTrue(logs.contains("private-payload"));
            assertTrue(logs.contains("private-report"));
            assertTrue(appender.list.stream().allMatch(event ->
                    event.getFormattedMessage().contains("private-payload")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getThrowableProxy() != null));
        } finally {
            RequestContextHolder.resetRequestAttributes();
            logger.detachAppender(appender);
        }
    }

    @Test
    void feedbackBodyAppearsInRequestAndResponseLogs() throws Throwable {
        var aspect = new ControllerLogAspect(new ObjectMapper());
        var point = mock(ProceedingJoinPoint.class);
        var signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{Map.of("content", "private-report-text")});
        when(point.proceed()).thenReturn(ApiResponse.success(Map.of("content", "private-report-text")));
        Logger logger = (Logger) LoggerFactory.getLogger(ControllerLogAspect.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (String path : new String[]{"/api/miniapp/feedbacks", "/api/admin/feedbacks"}) {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", path)));
                aspect.logController(point);
            }
            assertTrue(appender.list.stream().allMatch(event -> {
                String message = event.getFormattedMessage();
                return message.contains("入参：{\"arg0\":{\"content\":\"private-report-text\"}}")
                        && message.contains("\"data\":{\"content\":\"private-report-text\"}");
            }));
        } finally {
            RequestContextHolder.resetRequestAttributes();
            logger.detachAppender(appender);
        }
    }

    @Test
    void logControllerShouldKeepOriginalRequestAndResponseValues() throws Throwable {
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
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        try {
            assertEquals(response, aspect.logController(joinPoint));
        } finally {
            RequestContextHolder.resetRequestAttributes();
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertTrue(logs.contains("请求路径：/api/admin/auth/login"));
        assertTrue(logs.contains("方式：POST"));
        assertTrue(logs.contains("入参："));
        assertTrue(logs.contains("出参："));
        assertTrue(logs.contains("cover.png"));
        assertTrue(logs.contains("tester"));
        assertFalse(logs.contains("***"));
        assertTrue(logs.contains("request-secret"));
        assertTrue(logs.contains("wechat-code"));
        assertTrue(logs.contains("login-password"));
        assertTrue(logs.contains("response-secret"));
        assertFalse(logs.contains("binary-content"));
    }

}
