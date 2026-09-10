package com.personalink.server.aspect;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 统一记录 Controller 请求路径、请求方式、请求参数和响应结果。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerLogAspect {

    private static final Logger LOG = LoggerFactory.getLogger(ControllerLogAspect.class);

    private final ObjectMapper objectMapper;

    /**
     * 记录 Controller 方法调用。
     *
     * @param joinPoint Controller 方法连接点
     * @return Controller 返回结果
     * @throws Throwable Controller 执行异常
     */
    @Around("execution(public * com.personalink.server.controller..*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes == null ? null : requestAttributes.getRequest();
        String requestPath = request == null ? signature.toShortString() : request.getRequestURI();
        String requestMethod = request == null ? "UNKNOWN" : request.getMethod();
        String parameters = this.serializeParameters(signature.getParameterNames(), joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            LOG.info("[Controller] 请求路径：{}，方式：{}，入参：{}，出参：{}",
                    requestPath, requestMethod, parameters, this.toLogNode(result).toString());
            return result;
        } catch (Throwable throwable) {
            LOG.error("[Controller] 请求路径：{}，方式：{}，入参：{}，出参：异常",
                    requestPath, requestMethod, parameters, throwable);
            throw throwable;
        }
    }

    private String serializeParameters(String[] parameterNames, Object[] arguments) {
        ObjectNode parameters = this.objectMapper.createObjectNode();
        for (int index = 0; index < arguments.length; index++) {
            String name = parameterNames != null && parameterNames.length > index
                    ? parameterNames[index] : "arg" + index;
            parameters.set(name, this.toLogNode(arguments[index]));
        }
        return parameters.toString();
    }

    private JsonNode toLogNode(Object value) {
        if (value instanceof MultipartFile file) {
            ObjectNode fileNode = this.objectMapper.createObjectNode();
            fileNode.put("originalFilename", file.getOriginalFilename());
            fileNode.put("contentType", file.getContentType());
            fileNode.put("size", file.getSize());
            return fileNode;
        }
        if (value instanceof ServletRequest || value instanceof ServletResponse) {
            return this.objectMapper.getNodeFactory().textNode(value.getClass().getSimpleName());
        }
        try {
            return this.objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            return this.objectMapper.getNodeFactory().textNode("<无法序列化>");
        }
    }

}
