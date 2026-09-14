package com.personalink.server.controller.wechat;

import com.personalink.server.miniapp.security.WechatMediaCheckEvent;
import com.personalink.server.miniapp.security.WechatMessagePushCodec;
import com.personalink.server.service.MiniappUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 微信消息推送回调：服务器地址校验与图片内容安全审核结果接收。
 * <p>回调不携带业务会话，身份与结果由微信签名保证。
 * @author persona-link
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/wechat/message-push")
@RequiredArgsConstructor
public class WechatMessagePushController {

    /** 微信要求 5 秒内应答，无业务指令时回复固定成功标记。 */
    private static final String SUCCESS = "success";

    private final WechatMessagePushCodec messagePushCodec;
    private final MiniappUserService miniappUserService;

    /**
     * 校验服务器地址有效性。
     *
     * @param signature 明文模式签名
     * @param msgSignature 安全模式签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param echostr 微信回显串
     * @return 明文 echostr
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam(value = "signature", required = false) String signature,
                         @RequestParam(value = "msg_signature", required = false) String msgSignature,
                         @RequestParam("timestamp") String timestamp,
                         @RequestParam("nonce") String nonce,
                         @RequestParam("echostr") String echostr) {
        return this.messagePushCodec.verifyUrl(signature, msgSignature, timestamp, nonce, echostr);
    }

    /**
     * 接收事件推送：图片审核结果落到待审头像。
     *
     * @param signature 明文模式签名
     * @param msgSignature 安全模式签名
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param body 原始消息体
     * @return 固定成功标记
     */
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestParam(value = "signature", required = false) String signature,
                          @RequestParam(value = "msg_signature", required = false) String msgSignature,
                          @RequestParam("timestamp") String timestamp,
                          @RequestParam("nonce") String nonce,
                          @RequestBody String body) {
        String plainXml = this.messagePushCodec.decodeMessage(signature, msgSignature, timestamp, nonce, body);
        WechatMediaCheckEvent event = this.messagePushCodec.parseMediaCheckEvent(plainXml);
        if (Objects.nonNull(event)) {
            // 任务号已由微信签名保证来源，用户身份以库中待审记录为准。
            this.miniappUserService.applyAvatarAuditResult(event.traceId(), event.passed());
        }
        return SUCCESS;
    }
}
