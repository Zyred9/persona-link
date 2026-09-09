package com.personalink.server.dto;
import java.time.LocalDateTime;
/** 报告访问策略：1可查看，2需要广告；不含报告内容。 */
public record ReportAccessResponse(int status, String adUnitId, String taskId, LocalDateTime expiresAt) {}
