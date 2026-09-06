package com.personalink.server.ai;

/** 提交可能已计费但未取得任务 ID；禁止自动重提，以免重复计费。 */
public class ImageSubmissionUncertainException extends RuntimeException {
    public ImageSubmissionUncertainException(Throwable cause) {
        super("生图提交结果未知，请联系管理员核实上游任务，勿重复点击生成", cause);
    }
}
