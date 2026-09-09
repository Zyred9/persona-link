package com.personalink.server.controller.app;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.MiniappHomeResponse;
import com.personalink.server.dto.MiniappTestDetailResponse;
import com.personalink.server.service.MiniappContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序公开内容接口。
 */
@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class MiniappContentController {

    private final MiniappContentService miniappContentService;

    /**
     * 获取按题型首页位置配置的可展示内容。
     *
     * @return 首页分类、焦点推荐、推荐题型和全部可用题型
     */
    @GetMapping("/home")
    public ApiResponse<MiniappHomeResponse> home() {
        return ApiResponse.success(this.miniappContentService.getHome());
    }

    /**
     * 获取启用题型的当前发布版本详情。
     *
     * @param testId 题型 ID
     * @return 题型当前发布版本详情
     */
    @GetMapping("/tests/{testId}")
    public ApiResponse<MiniappTestDetailResponse> testDetail(@PathVariable Long testId) {
        return ApiResponse.success(this.miniappContentService.getPublishedTestDetail(testId));
    }
}
