package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.personalink.server.util.ImageResolutionUtil;
import com.personalink.server.util.ImageDimensionDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Objects;

/** AI 生图关键词和幂等请求标识。 */
public record ImageGenerationStartRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,
        @NotBlank @Size(max = 1500) String promptText,
        @JsonDeserialize(using = ImageDimensionDeserializer.class) Integer coverWidth,
        @JsonDeserialize(using = ImageDimensionDeserializer.class) Integer coverHeight,
        @JsonDeserialize(using = ImageDimensionDeserializer.class) Integer detailWidth,
        @JsonDeserialize(using = ImageDimensionDeserializer.class) Integer detailHeight) {
    public ImageGenerationStartRequest {
        coverWidth = Objects.isNull(coverWidth) ? 800 : coverWidth;
        coverHeight = Objects.isNull(coverHeight) ? 800 : coverHeight;
        detailWidth = Objects.isNull(detailWidth) ? 1100 : detailWidth;
        detailHeight = Objects.isNull(detailHeight) ? 500 : detailHeight;
    }

    @AssertTrue(message = "图片宽高须为至少256像素的整数，总像素262144至4194304，宽高比1:3至3:1")
    public boolean isResolutionValid() {
        return ImageResolutionUtil.isValid(coverWidth, coverHeight)
                && ImageResolutionUtil.isValid(detailWidth, detailHeight);
    }
}
