package com.personalink.server.dto;

/** 已发布协议版本摘要，供小程序检查是否需要重新同意，不传输正文。 */
public record LegalDocumentVersionResponse(int type, long version) {
}
