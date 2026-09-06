package com.personalink.server.ai;

import com.personalink.server.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 仅表示提交前校验失败或上游明确未受理；允许安全解除提交占用标记。 */
public class ImageSubmissionRejectedException extends BusinessException {
    public ImageSubmissionRejectedException(String message) {
        super(HttpStatus.BAD_GATEWAY, 50261, message);
    }
}
