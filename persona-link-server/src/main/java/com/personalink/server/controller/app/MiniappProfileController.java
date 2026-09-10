package com.personalink.server.controller.app;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.MiniappAvatarResponse;
import com.personalink.server.dto.MiniappProfileRequest;
import com.personalink.server.dto.MiniappProfileResponse;
import com.personalink.server.service.MiniappUserService;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 当前登录用户的资料接口。 */
@RestController
@RequestMapping("/api/miniapp/profile")
@RequiredArgsConstructor
public class MiniappProfileController {
    private final AuthService authService;
    private final MiniappUserService miniappUserService;

    /**
     * 查询当前微信用户资料。
     * @param authorization 业务会话令牌
     * @return 昵称与头像，未完善字段为空串
     */
    @GetMapping
    public ApiResponse<MiniappProfileResponse> profile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(this.miniappUserService.profile(this.authService.requireSession(authorization).openId()));
    }

    /**
     * 保存当前用户主动填写的资料。
     * @param authorization 业务会话令牌
     * @param request 昵称和已上传头像
     * @return 保存后的资料
     */
    @PutMapping
    public ApiResponse<MiniappProfileResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody MiniappProfileRequest request) {
        return ApiResponse.success(this.miniappUserService.updateProfile(
                this.authService.requireSession(authorization).openId(), request));
    }

    /**
     * 上传头像并立即绑定当前账号。
     * @param authorization 业务会话令牌
     * @param file PNG、JPG 或 WebP 图片，最大 5 MB
     * @return 已保存的头像地址
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MiniappAvatarResponse> avatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(this.miniappUserService.uploadAvatar(
                this.authService.requireSession(authorization).openId(), file));
    }
}
