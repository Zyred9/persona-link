package com.personalink.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalink.server.config.ImageGenerationProperties;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.util.VolcImageSignUtil;
import com.personalink.server.util.ImageResolutionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** 两家原生异步生图接口；提交只执行一次，查询使用提交时的路由快照。 */
@Component
public class ImageProviderClient {
    private static final int QWEN = 1;
    private static final int VOLC = 2;
    private static final int PENDING = 1;
    private static final int SUCCEEDED = 2;
    private static final int FAILED = 3;
    private final ImageGenerationProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    public ImageProviderClient(ImageGenerationProperties properties, ObjectMapper mapper) {
        this(properties, mapper, createClient(properties));
    }

    ImageProviderClient(ImageGenerationProperties properties, ObjectMapper mapper, RestClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
    }

    private static RestClient createClient(ImageGenerationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        return RestClient.builder().requestFactory(factory).build();
    }

    public ImageProviderRoute currentRoute() {
        return switch (this.properties.getProvider()) {
            case QWEN -> new ImageProviderRoute(QWEN, this.properties.getQwenModel(),
                    this.properties.getQwenBaseUrl(), "");
            case VOLC -> new ImageProviderRoute(VOLC, this.properties.getVolcModel(),
                    this.properties.getVolcBaseUrl(), this.properties.getVolcRegion());
            default -> throw this.configurationError();
        };
    }

    public void requireConfigured(ImageProviderRoute route) {
        if (route == null || !StringUtils.hasText(route.modelName())) {
            throw this.configurationError();
        }
        URI uri;
        try {
            uri = URI.create(route.baseUrl());
        } catch (RuntimeException exception) {
            throw this.configurationError();
        }
        String host = uri.getHost();
        boolean qwenHost = host != null && ("dashscope.aliyuncs.com".equals(host)
                || "dashscope-intl.aliyuncs.com".equals(host)
                || host.endsWith(".maas.aliyuncs.com"));
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !("".equals(uri.getPath()) || "/".equals(uri.getPath()))) {
            throw this.configurationError();
        }
        if (QWEN == route.provider()) {
            if (!qwenHost || !StringUtils.hasText(this.properties.getQwenApiKey())
                    || !("qwen-image-3.0".equals(route.modelName())
                    || "qwen-image-3.0-pro".equals(route.modelName()))) {
                throw this.configurationError();
            }
        } else if (VOLC == route.provider()) {
            if (!"visual.volcengineapi.com".equals(host)
                    || !StringUtils.hasText(this.properties.getVolcAccessKey())
                    || !StringUtils.hasText(this.properties.getVolcSecretKey())
                    || !"cn-north-1".equals(route.region())
                    || !("jimeng_t2i_v40".equals(route.modelName())
                    || "t2i_v40_jimeng".equals(route.modelName()))) {
                throw this.configurationError();
            }
        } else {
            throw this.configurationError();
        }
    }

    public String submit(ImageProviderRoute route, String prompt, int width, int height) {
        try {
            this.requireConfigured(route);
        } catch (BusinessException exception) {
            throw new ImageSubmissionRejectedException(exception.getMessage());
        }
        if (!StringUtils.hasText(prompt) || prompt.length() > 2100) {
            throw new ImageSubmissionRejectedException("生图提示词不能为空且不能超过2100字符");
        }
        if (!ImageResolutionUtil.isValid(width, height)) {
            throw new ImageSubmissionRejectedException("生图分辨率超出支持范围");
        }
        try {
            return this.submitOnce(route, prompt, width, height);
        } catch (ImageSubmissionRejectedException | ImageSubmissionUncertainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 只有显式拒绝可安全重提；未知运行时异常也可能发生在上游已经受理之后。
            throw new ImageSubmissionUncertainException(exception);
        }
    }

    private String submitOnce(ImageProviderRoute route, String prompt, int width, int height) {
        ObjectNode body = this.mapper.createObjectNode();
        if (QWEN == route.provider()) {
            body.put("model", route.modelName());
            body.putObject("input").putArray("messages").addObject().put("role", "user")
                    .putArray("content").addObject().put("text", prompt);
            body.putObject("parameters").put("n", 1).put("size", width + "*" + height)
                    .put("prompt_extend", false).put("watermark", true);
        } else {
            // 即梦至少1024²像素；整数倍放大保持输入比例，转存时回到目标尺寸。
            int scale = (int) Math.ceil(Math.sqrt(1048576.0 / ((long) width * height)));
            body.put("req_key", route.modelName()).put("prompt", prompt).put("force_single", true)
                    .put("width", width * scale).put("height", height * scale);
        }
        JsonNode result = this.request(route, body, null, true);
        String taskId = result.path(QWEN == route.provider() ? "output" : "data").path("task_id").asText();
        if (!StringUtils.hasText(taskId) || !taskId.matches("[A-Za-z0-9_-]{1,200}")) {
            throw new ImageSubmissionUncertainException(null);
        }
        return taskId;
    }

    public ImageProviderResult query(ImageProviderRoute route, String taskId) {
        this.requireConfigured(route);
        if (taskId == null || !taskId.matches("[A-Za-z0-9_-]{1,200}")) {
            throw new BusinessException(40061, "生图上游任务编号不合法");
        }
        ObjectNode body = this.mapper.createObjectNode().put("req_key", route.modelName())
                .put("task_id", taskId).put("req_json", "{\"return_url\":true,\"logo_info\":{\"add_logo\":true}}");
        JsonNode result = this.request(route, body, taskId, false);
        JsonNode output = result.path(QWEN == route.provider() ? "output" : "data");
        String state = output.path(QWEN == route.provider() ? "task_status" : "status").asText();
        if ("PENDING".equals(state) || "RUNNING".equals(state) || "in_queue".equals(state)
                || "generating".equals(state)) {
            return new ImageProviderResult(PENDING, null, null);
        }
        if ("SUCCEEDED".equals(state) || "done".equals(state)) {
            String url = QWEN == route.provider()
                    ? output.path("choices").path(0).path("message").path("content").path(0).path("image").asText()
                    : output.path("image_urls").path(0).asText();
            if (StringUtils.hasText(url)) {
                return new ImageProviderResult(SUCCEEDED, url, null);
            }
            return new ImageProviderResult(FAILED, null, "生图服务已结束任务但未返回图片，可能被内容审核拦截");
        }
        if ("FAILED".equals(state) || "CANCELED".equals(state) || "UNKNOWN".equals(state)
                || "expired".equals(state) || "not_found".equals(state) || "failed".equals(state)) {
            return new ImageProviderResult(FAILED, null, "上游生图任务失败、已取消或已过期，请检查提示词和服务控制台");
        }
        throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261, "生图查询返回未知状态，请稍后查询");
    }

    private JsonNode request(ImageProviderRoute route, ObjectNode body, String taskId, boolean submitting) {
        String baseUrl = route.baseUrl().replaceAll("/$", "");
        String path = QWEN == route.provider()
                ? submitting ? "/api/v1/services/aigc/image-generation/generation" : "/api/v1/tasks/" + taskId
                : "/?Action=" + (submitting ? "CVSync2AsyncSubmitTask" : "CVSync2AsyncGetResult")
                        + "&Version=2022-08-31";
        HttpMethod method = QWEN == route.provider() && !submitting ? HttpMethod.GET : HttpMethod.POST;
        String payload = body.toString();
        Map<String, String> headers = QWEN == route.provider()
                ? Map.of("Authorization", "Bearer " + this.properties.getQwenApiKey(),
                        "Content-Type", "application/json", "X-DashScope-Async", "enable")
                : VolcImageSignUtil.headers(this.properties.getVolcAccessKey(), this.properties.getVolcSecretKey(),
                        route.region(), URI.create(baseUrl).getHost(), path.substring(2), payload, Instant.now());
        try {
            RestClient.RequestBodySpec request = this.client.method(method).uri(URI.create(baseUrl + path))
                    .headers(values -> headers.forEach(values::set));
            if (HttpMethod.POST == method) {
                request.body(payload);
            }
            return request.exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                if (!response.getStatusCode().is2xxSuccessful()) {
                    if (submitting) {
                        if (status >= 400 && status < 500 && status != 408) {
                            throw new ImageSubmissionRejectedException(
                                    "生图服务未受理请求（HTTP " + status + "），请检查配置、提示词或余额");
                        }
                        throw new ImageSubmissionUncertainException(null);
                    }
                    throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261,
                            "生图服务请求失败（HTTP " + status + "），请检查后端配置、余额或稍后查询");
                }
                JsonNode root;
                try {
                    root = this.mapper.readTree(response.getBody());
                } catch (IOException exception) {
                    if (submitting) { throw new ImageSubmissionUncertainException(exception); }
                    throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261, "生图查询响应无法解析，请稍后查询");
                }
                if (root == null) {
                    if (submitting) { throw new ImageSubmissionUncertainException(null); }
                    throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261, "生图查询响应为空");
                }
                if (VOLC == route.provider() && !root.path("code").canConvertToInt()) {
                    if (submitting) { throw new ImageSubmissionUncertainException(null); }
                    throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261, "生图查询响应缺少状态码");
                }
                if ((VOLC == route.provider() && root.path("code").asInt() != 10000)
                        || (QWEN == route.provider() && root.hasNonNull("code"))) {
                    if (submitting) {
                        // 非成功业务码可能是受理后的内部错误；没有确切拒绝语义时禁止自动重提。
                        throw new ImageSubmissionUncertainException(null);
                    }
                    throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261,
                            "生图服务拒绝请求，请检查提示词、模型权限、余额及服务控制台");
                }
                return root;
            });
        } catch (RestClientException exception) {
            if (submitting) { throw new ImageSubmissionUncertainException(exception); }
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50261, "生图查询暂不可用，请稍后查询");
        }
    }

    private BusinessException configurationError() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50361,
                "生图服务未配置或路由不受支持，请配置千问 Qwen-Image-3.0 或火山即梦4的官方地址、模型和密钥");
    }
}
