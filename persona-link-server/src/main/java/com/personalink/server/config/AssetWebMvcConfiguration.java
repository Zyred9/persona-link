package com.personalink.server.config;

import com.personalink.server.service.impl.LocalAssetService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地上传图片访问配置。
 */
@Configuration
public class AssetWebMvcConfiguration implements WebMvcConfigurer {

    private final LocalAssetService localAssetService;

    public AssetWebMvcConfiguration(LocalAssetService localAssetService) {
        this.localAssetService = localAssetService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = this.localAssetService.getUploadDirectory().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
