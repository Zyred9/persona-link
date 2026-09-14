package com.personalink.server.miniapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatMessagePushCodecTest {

    private static final String TOKEN = "push-token";
    private static final String ENCODING_AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String APP_ID = "wx-app-id";
    private static final String TIMESTAMP = "1700000000";
    private static final String NONCE = "nonce-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WechatMessagePushCodec codec = new WechatMessagePushCodec(TOKEN, ENCODING_AES_KEY, APP_ID, this.objectMapper);

    @Test
    void verifiesPlainModeCallbackAndMessage() {
        String signature = sign(TOKEN, TIMESTAMP, NONCE);
        assertEquals("echo-1", this.codec.verifyUrl(signature, null, TIMESTAMP, NONCE, "echo-1"));
        assertEquals("<xml><hi/></xml>", this.codec.decodeMessage(signature, null, TIMESTAMP, NONCE, "<xml><hi/></xml>"));
    }

    @Test
    void rejectsForgedPlainSignature() {
        String signature = sign(TOKEN, TIMESTAMP, NONCE);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.codec.decodeMessage(signature, null, TIMESTAMP, "other-nonce", "<xml/>"));
        assertEquals(403, exception.getHttpStatus().value());
    }

    @Test
    void verifiesSecureModeCallbackAndDecryptsMessage() throws GeneralSecurityException {
        String encryptedEcho = encrypt("echo-1", APP_ID);
        assertEquals("echo-1", this.codec.verifyUrl(null, sign(TOKEN, TIMESTAMP, NONCE, encryptedEcho),
                TIMESTAMP, NONCE, encryptedEcho));

        String plainXml = "<xml><Event><![CDATA[wxa_media_check]]></Event></xml>";
        String encrypted = encrypt(plainXml, APP_ID);
        assertEquals(plainXml, this.codec.decodeMessage(null, sign(TOKEN, TIMESTAMP, NONCE, encrypted),
                TIMESTAMP, NONCE, "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>"));
    }

    @Test
    void rejectsTamperedSecureMessageAndForeignAppId() throws GeneralSecurityException {
        String encrypted = encrypt("<xml><ok/></xml>", APP_ID);
        String foreignSignature = sign(TOKEN, TIMESTAMP, NONCE, encrypt("<xml><other/></xml>", APP_ID));
        assertThrows(BusinessException.class, () -> this.codec.decodeMessage(null, foreignSignature,
                TIMESTAMP, NONCE, "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>"));

        String foreignAppId = encrypt("<xml><ok/></xml>", "wx-other");
        assertThrows(BusinessException.class, () -> this.codec.decodeMessage(null,
                sign(TOKEN, TIMESTAMP, NONCE, foreignAppId), TIMESTAMP, NONCE,
                "<xml><Encrypt><![CDATA[" + foreignAppId + "]]></Encrypt></xml>"));
    }

    @Test
    void rejectsUnconfiguredPushToken() {
        WechatMessagePushCodec unconfigured = new WechatMessagePushCodec("", "", "", this.objectMapper);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> unconfigured.decodeMessage(sign(TOKEN, TIMESTAMP, NONCE), null, TIMESTAMP, NONCE, "<xml/>"));
        assertEquals(503, exception.getHttpStatus().value());
        assertEquals(50361, exception.getCode());
    }

    @Test
    void parsesPassedMediaCheckEvent() {
        WechatMediaCheckEvent event = this.codec.parseMediaCheckEvent(mediaCheckXml("pass"));
        assertEquals("openid-a", event.openId());
        assertEquals("trace-1", event.traceId());
        assertTrue(event.passed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pass", "risky", "review"})
    void parsesNestedXmlAndJsonResults(String suggestion) {
        String xml = "<xml><Event>wxa_media_check</Event><trace_id>trace-1</trace_id>"
                + "<result><suggest>" + suggestion + "</suggest><label>100</label></result></xml>";
        String json = "{\"Event\":\"wxa_media_check\",\"trace_id\":\"trace-1\","
                + "\"result\":{\"suggest\":\"" + suggestion + "\",\"label\":100}}";
        String signature = sign(TOKEN, TIMESTAMP, NONCE);
        for (String body : List.of(xml, json)) {
            WechatMediaCheckEvent event = this.codec.parseMediaCheckEvent(
                    this.codec.decodeMessage(signature, null, TIMESTAMP, NONCE, body));
            assertEquals("trace-1", event.traceId());
            assertEquals("pass".equals(suggestion), event.passed());
        }
    }

    @Test
    void decryptsJsonEnvelopeAndParsesJsonEvent() throws GeneralSecurityException {
        String json = "{\"Event\":\"wxa_media_check\",\"trace_id\":\"trace-1\",\"result\":{\"suggest\":\"pass\"}}";
        String encrypted = encrypt(json, APP_ID);
        String body = "{\"Encrypt\":\"" + encrypted + "\"}";
        String signature = sign(TOKEN, TIMESTAMP, NONCE, encrypted);
        assertTrue(this.codec.parseMediaCheckEvent(this.codec.decodeMessage(null, signature,
                TIMESTAMP, NONCE, body)).passed());
        assertThrows(BusinessException.class, () -> this.codec.decodeMessage(null, signature,
                TIMESTAMP, "tampered", body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid", "<xml>", "[]", "<!DOCTYPE xml [<!ENTITY secret SYSTEM 'file:///never-read'>]><xml>&secret;</xml>"})
    void rejectsMalformedMessagesAndExternalEntities(String body) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> this.codec.parseMediaCheckEvent(body));
        assertEquals(40007, error.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"risky", "review", "unknown", ""})
    void treatsUnpassedSuggestionsAsNotPassed(String suggestion) {
        assertFalse(this.codec.parseMediaCheckEvent(mediaCheckXml(suggestion)).passed());
    }

    @Test
    void ignoresOtherEventsAndMissingTraceId() {
        assertNull(this.codec.parseMediaCheckEvent(
                "<xml><Event><![CDATA[subscribe]]></Event></xml>"), "非图片审核事件直接忽略");
        assertNull(this.codec.parseMediaCheckEvent(
                "<xml><Event><![CDATA[wxa_media_check]]></Event><result><![CDATA[{\"suggest\":\"pass\"}]]></result></xml>"),
                "缺少任务号无法关联待审头像");
    }

    private static String mediaCheckXml(String suggestion) {
        return "<xml><FromUserName><![CDATA[openid-a]]></FromUserName>"
                + "<Event><![CDATA[wxa_media_check]]></Event>"
                + "<trace_id><![CDATA[trace-1]]></trace_id>"
                + "<result><![CDATA[{\"suggest\":\"" + suggestion + "\"}]]></result></xml>";
    }

    /** 按微信协议构造安全模式密文：随机 16 字节 + 消息长度 + 消息 + AppID，32 字节块填充。 */
    private static String encrypt(String message, String appId) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(ENCODING_AES_KEY + "=");
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] appIdBytes = appId.getBytes(StandardCharsets.UTF_8);
        byte[] plain = new byte[16 + 4 + messageBytes.length + appIdBytes.length];
        for (int index = 0; index < 16; index++) {
            plain[index] = (byte) (index + 1);
        }
        ByteBuffer.wrap(plain, 16, 4).putInt(messageBytes.length);
        System.arraycopy(messageBytes, 0, plain, 20, messageBytes.length);
        System.arraycopy(appIdBytes, 0, plain, 20 + messageBytes.length, appIdBytes.length);
        int padding = 32 - (plain.length % 32);
        byte[] padded = Arrays.copyOf(plain, plain.length + padding);
        Arrays.fill(padded, plain.length, padded.length, (byte) padding);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(Arrays.copyOf(key, 16)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    }

    private static String sign(String... parts) {
        List<String> values = new ArrayList<>(Arrays.asList(parts));
        values.sort(Comparator.naturalOrder());
        return sha1Hex(String.join("", values));
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
            throw new IllegalStateException(exception);
        }
    }
}
