package com.personalink.server.controller;

import com.personalink.server.controller.wechat.WechatMessagePushController;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.exception.GlobalExceptionHandler;
import com.personalink.server.miniapp.security.WechatMediaCheckEvent;
import com.personalink.server.miniapp.security.WechatMessagePushCodec;
import com.personalink.server.service.MiniappUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WechatMessagePushControllerTest {

    @Test
    void verifyEchoesDecryptedString() throws Exception {
        WechatMessagePushCodec codec = mock(WechatMessagePushCodec.class);
        when(codec.verifyUrl("sig", "msg-sig", "ts", "nonce", "echo-1")).thenReturn("echo-1");
        var mvc = MockMvcBuilders.standaloneSetup(
                        new WechatMessagePushController(codec, mock(MiniappUserService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/wechat/message-push")
                        .param("signature", "sig").param("msg_signature", "msg-sig")
                        .param("timestamp", "ts").param("nonce", "nonce").param("echostr", "echo-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("echo-1"));
    }

    @Test
    void receiveAppliesMediaCheckResultAndAcks() throws Exception {
        WechatMessagePushCodec codec = mock(WechatMessagePushCodec.class);
        MiniappUserService users = mock(MiniappUserService.class);
        when(codec.decodeMessage(any(), any(), any(), any(), any())).thenReturn("<xml/>");
        when(codec.parseMediaCheckEvent("<xml/>")).thenReturn(new WechatMediaCheckEvent("openid-a", "trace-1", true));
        var mvc = MockMvcBuilders.standaloneSetup(new WechatMessagePushController(codec, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/wechat/message-push")
                        .param("timestamp", "ts").param("nonce", "nonce")
                        .contentType(MediaType.TEXT_XML).content("<xml/>"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
        verify(users).applyAvatarAuditResult("trace-1", true);
    }

    @Test
    void receiveIgnoresNonMediaEvents() throws Exception {
        WechatMessagePushCodec codec = mock(WechatMessagePushCodec.class);
        MiniappUserService users = mock(MiniappUserService.class);
        when(codec.decodeMessage(any(), any(), any(), any(), any())).thenReturn("<xml/>");
        when(codec.parseMediaCheckEvent("<xml/>")).thenReturn(null);
        var mvc = MockMvcBuilders.standaloneSetup(new WechatMessagePushController(codec, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/wechat/message-push")
                        .param("timestamp", "ts").param("nonce", "nonce")
                        .contentType(MediaType.TEXT_XML).content("<xml/>"))
                .andExpect(status().isOk());
        verifyNoInteractions(users);
    }

    @Test
    void receiveRejectsForgedSignatureBeforeBusiness() throws Exception {
        WechatMessagePushCodec codec = mock(WechatMessagePushCodec.class);
        MiniappUserService users = mock(MiniappUserService.class);
        when(codec.decodeMessage(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, 40301, "消息签名校验失败"));
        var mvc = MockMvcBuilders.standaloneSetup(new WechatMessagePushController(codec, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/wechat/message-push")
                        .param("timestamp", "ts").param("nonce", "nonce")
                        .contentType(MediaType.TEXT_XML).content("<xml/>"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }
}
