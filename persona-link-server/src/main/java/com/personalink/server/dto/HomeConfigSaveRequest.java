package com.personalink.server.dto;

import com.personalink.server.util.MiniappImageUrlUtil;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** 首页标题图配置；清空后回退小程序默认图。 */
public record HomeConfigSaveRequest(@Size(max = 1024, message = "首页标题图地址不能超过1024字符") String titleImageUrl) {
    public HomeConfigSaveRequest {
        titleImageUrl = MiniappImageUrlUtil.normalize(titleImageUrl);
    }

    @AssertTrue(message = "首页标题图须为HTTP(S)地址或/uploads/下的图片路径")
    public boolean isTitleImageUrlValid() {
        return MiniappImageUrlUtil.isValid(titleImageUrl);
    }
}
