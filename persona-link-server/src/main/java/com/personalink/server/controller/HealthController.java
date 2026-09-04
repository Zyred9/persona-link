package com.personalink.server.controller;

import com.personalink.server.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 查询服务运行状态。
     *
     * @return 服务状态
     */
    @GetMapping
    public ApiResponse<String> health() {
        return ApiResponse.success("UP");
    }
}
