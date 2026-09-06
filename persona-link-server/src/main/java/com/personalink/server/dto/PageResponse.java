package com.personalink.server.dto;

import java.util.List;

/**
 * 统一分页响应。
 *
 * @param records 当前页记录
 * @param total 总记录数
 * @param page 当前页码
 * @param size 每页数量
 * @param <T> 记录类型
 */
public record PageResponse<T>(List<T> records, long total, long page, long size) {
}
