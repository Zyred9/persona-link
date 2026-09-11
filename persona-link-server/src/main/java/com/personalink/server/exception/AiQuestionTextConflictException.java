package com.personalink.server.exception;

/** AI 题干与既有内容冲突，可重新生成当前批次；不用于题号或数据库故障。 */
public class AiQuestionTextConflictException extends BusinessException {

    public AiQuestionTextConflictException(String message) {
        super(400, message);
    }
}
