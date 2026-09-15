package com.personalink.server.miniapp.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 微信消息推送回调的验签、安全模式解密与事件解析。
 * <p>只按微信官方协议处理：明文模式校验 signature，安全模式校验 msg_signature 并 AES 解密；
 * 验签失败的请求一律拒绝，不区分错误细节。
 * @author persona-link
 * @since 1.0.0
 */
@Component
public class WechatMessagePushCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatMessagePushCodec.class);
    /** 图片审核结果事件名。 */
    private static final String MEDIA_CHECK_EVENT = "wxa_media_check";
    /** 微信明确通过的结论。 */
    private static final Set<String> PASS_SUGGESTIONS = Set.of("pass");
    /** 微信明确不通过的结论。 */
    private static final Set<String> RISKY_SUGGESTIONS = Set.of("risky", "review");
    /** 安全模式 AES 填充上限：按微信协议使用 32 字节填充块。 */
    private static final int MAX_PADDING_BYTES = 32;
    /** 安全模式消息体头部长度：16 字节随机数 + 4 字节消息长度。 */
    private static final int MESSAGE_HEADER_BYTES = 20;

    private final String messageToken;
    private final String encodingAesKey;
    private final String appId;
    private final ObjectMapper objectMapper;

    @Autowired
    public WechatMessagePushCodec(@Value("${wechat.message-token:}") String messageToken,
                                  @Value("${wechat.message-aes-key:}") String encodingAesKey,
                                  @Value("${wechat.app-id:}") String appId,
                                  ObjectMapper objectMapper) {
        this.messageToken = messageToken;
        this.encodingAesKey = encodingAesKey;
        this.appId = appId;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验服务器地址有效性。
     *
     * @param signature 明文模式签名
     * @param msgSignature 安全模式签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param echostr 微信回显串
     * @return 明文 echostr
     * @throws BusinessException 验签失败或令牌未配置
     */
    public String verifyUrl(String signature, String msgSignature, String timestamp, String nonce, String echostr) {
        if (StringUtils.hasText(msgSignature)) {
            if (!this.signatureMatches(msgSignature, timestamp, nonce, echostr)) {
                throw this.forbidden();
            }
            return this.decrypt(echostr);
        }
        if (!this.signatureMatches(signature, timestamp, nonce)) {
            throw this.forbidden();
        }
        return echostr;
    }

    /**
     * 校验消息推送签名并返回明文消息体。
     *
     * @param signature 明文模式签名
     * @param msgSignature 安全模式签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param body 原始请求体
     * @return 明文 XML 或 JSON
     * @throws BusinessException 验签失败、解密失败或消息格式异常
     */
    public String decodeMessage(String signature, String msgSignature, String timestamp, String nonce, String body) {
        if (StringUtils.hasText(msgSignature)) {
            String encrypted = this.extractEncrypted(body);
            if (!StringUtils.hasText(encrypted) || !this.signatureMatches(msgSignature, timestamp, nonce, encrypted)) {
                throw this.forbidden();
            }
            return this.decrypt(encrypted);
        }
        if (!this.signatureMatches(signature, timestamp, nonce)) {
            throw this.forbidden();
        }
        return body;
    }

    /**
     * 解析图片审核结果事件。
     *
     * @param plainXml 已验签的明文消息体
     * @return 图片审核事件；非图片审核事件或缺少任务号时返回 null
     */
    public WechatMediaCheckEvent parseMediaCheckEvent(String plainXml) {
        Map<String, String> fields = this.readFields(plainXml);
        if (!MEDIA_CHECK_EVENT.equals(fields.get("Event"))) {
            return null;
        }
        String traceId = fields.get("trace_id");
        if (!StringUtils.hasText(traceId)) {
            LOGGER.warn("[微信消息推送] 图片审核事件缺少任务号，已忽略");
            return null;
        }
        String suggestion = this.readSuggestion(fields.get("result"));
        if (!PASS_SUGGESTIONS.contains(suggestion) && !RISKY_SUGGESTIONS.contains(suggestion)) {
            // 结论缺失或未知时按不通过处理，头像宁可退回上一张也不放行未审图片。
            LOGGER.warn("[微信消息推送] 图片审核结论未知，任务号：{}", traceId);
        }
        return new WechatMediaCheckEvent(fields.get("FromUserName"), traceId, PASS_SUGGESTIONS.contains(suggestion));
    }

    /** 微信签名：token、时间戳、随机数与密文分别排序后拼接取 SHA-1。 */
    private boolean signatureMatches(String expected, String timestamp, String nonce, String... parts) {
        if (!StringUtils.hasText(this.messageToken)) {
            LOGGER.warn("[微信消息推送] 消息推送令牌未配置，无法校验签名");
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50361, "微信消息推送未配置");
        }
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        List<String> values = new ArrayList<>(parts.length + 3);
        values.add(this.messageToken);
        values.add(timestamp);
        values.add(nonce);
        values.addAll(Arrays.asList(parts));
        values.sort(Comparator.naturalOrder());
        String computed = sha1Hex(String.join("", values));
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private String extractEncrypted(String body) {
        return this.readFields(body).getOrDefault("Encrypt", "");
    }

    private String decrypt(String encrypted) {
        if (!StringUtils.hasText(this.encodingAesKey)) {
            LOGGER.warn("[微信消息推送] EncodingAESKey 未配置，无法解密安全模式消息");
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50361, "微信消息推送未配置");
        }
        try {
            byte[] key = Base64.getDecoder().decode(this.encodingAesKey + "=");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(Arrays.copyOf(key, 16)));
            return this.readMessage(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            LOGGER.warn("[微信消息推送] 密文解密失败", exception);
            throw this.forbidden();
        }
    }

    /** 解出安全模式消息体：16 字节随机数 + 4 字节长度 + 消息 + AppID。 */
    private String readMessage(byte[] plain) {
        if (plain.length < MESSAGE_HEADER_BYTES) {
            throw new IllegalArgumentException("消息长度非法");
        }
        int padding = plain[plain.length - 1] & 0xFF;
        int contentLength = ByteBuffer.wrap(plain, 16, 4).getInt();
        // 消息后还有 AppID 尾巴，长度校验只排除越界与非法填充。
        if (padding < 1 || padding > MAX_PADDING_BYTES || contentLength < 0
                || MESSAGE_HEADER_BYTES + contentLength + padding > plain.length) {
            throw new IllegalArgumentException("消息长度非法");
        }
        String message = new String(plain, MESSAGE_HEADER_BYTES, contentLength, StandardCharsets.UTF_8);
        String messageAppId = new String(plain, MESSAGE_HEADER_BYTES + contentLength,
                plain.length - padding - MESSAGE_HEADER_BYTES - contentLength, StandardCharsets.UTF_8);
        if (StringUtils.hasText(this.appId) && !this.appId.equals(messageAppId)) {
            throw new IllegalArgumentException("AppID 不匹配");
        }
        return message;
    }

    private String readSuggestion(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return "";
        }
        try {
            return this.objectMapper.readTree(resultJson).path("suggest").asText();
        } catch (JsonProcessingException exception) {
            LOGGER.warn("[微信消息推送] 图片审核结果 JSON 解析失败", exception);
            return "";
        }
    }

    /** 只读取根节点一层子元素；禁用 DTD 与外部实体，避免 XML 注入。 */
    private Map<String, String> readFields(String plainXml) {
        try {
            if (StringUtils.hasText(plainXml) && plainXml.stripLeading().startsWith("{")) {
                JsonNode root = this.objectMapper.readTree(plainXml);
                Map<String, String> fields = new HashMap<>();
                root.fields().forEachRemaining(field -> fields.put(field.getKey(),
                        field.getValue().isTextual() ? field.getValue().textValue() : field.getValue().toString()));
                return fields;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(plainXml)));
            Map<String, String> fields = new HashMap<>();
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node node = children.item(index);
                if (Node.ELEMENT_NODE == node.getNodeType()) {
                    fields.put(node.getNodeName(), node.getTextContent());
                    // XML 的 result 是嵌套元素；旧 CDATA JSON 保持原样。
                    if ("result".equals(node.getNodeName())) {
                        NodeList resultChildren = node.getChildNodes();
                        for (int childIndex = 0; childIndex < resultChildren.getLength(); childIndex++) {
                            Node child = resultChildren.item(childIndex);
                            if (Node.ELEMENT_NODE == child.getNodeType() && "suggest".equals(child.getNodeName())) {
                                fields.put("result", this.objectMapper.createObjectNode()
                                        .put("suggest", child.getTextContent()).toString());
                            }
                        }
                    }
                }
            }
            return fields;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            LOGGER.warn("[微信消息推送] 消息体解析失败", exception);
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40007, "消息格式异常");
        }
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, 40301, "消息签名校验失败");
    }

    private static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(Character.forDigit((item >> 4) & 0xF, 16));
                hex.append(Character.forDigit(item & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-1 实现", exception);
        }
    }
}
