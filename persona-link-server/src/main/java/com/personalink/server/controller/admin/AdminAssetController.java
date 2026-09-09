package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.AssetResponse;
import com.personalink.server.dto.AssetItemResponse;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.service.impl.LocalAssetService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 后台图片资源接口。
 */
@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
public class AdminAssetController {

    private final LocalAssetService localAssetService;

    /**
     * 上传题型 Icon 或封面图。
     *
     * @param file 图片文件
     * @return 图片访问地址
     */
    @PostMapping("/images")
    public ApiResponse<AssetResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(this.localAssetService.saveImage(file));
    }

    /**
     * 查询图片素材历史。
     *
     * @param keyword 文件名关键字
     * @return 素材列表
     */
    @GetMapping("/images")
    public ApiResponse<List<AssetItemResponse>> listImages(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(this.localAssetService.listImages(keyword));
    }

    /**
     * 删除未被引用的图片素材。
     *
     * @param fileName 文件名
     * @return 空响应
     */
    @DeleteMapping("/images/{fileName}")
    public ApiResponse<Void> deleteImage(@PathVariable String fileName) {
        this.localAssetService.deleteImage(fileName);
        return ApiResponse.success(null);
    }
}
