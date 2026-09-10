package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.AppConfigSaveRequest;
import com.personalink.server.entity.AppConfigEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.AppConfigMapper;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppConfigManagementTest {
    @Test
    void validatesTypesAndPreservesBuiltInConstraints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var request : new AppConfigSaveRequest[]{
                    new AppConfigSaveRequest("custom", "", 1, "字符串", null),
                    new AppConfigSaveRequest("custom", "-1.2e3", 2, "数字", ""),
                    new AppConfigSaveRequest("custom", "false", 3, "布尔", ""),
                    new AppConfigSaveRequest("custom", "{\"a\":[1,true]}", 4, "JSON", ""),
                    new AppConfigSaveRequest("miniapp.version", " 1.2.3 ", 1, "版本", ""),
                    new AppConfigSaveRequest("miniapp.home.title_image_url", " HTTPS://example.com/a.png ", 1, "头图", "")}) {
                assertTrue(validator.validate(request).isEmpty(), request.toString());
            }
            for (var request : new AppConfigSaveRequest[]{
                    new AppConfigSaveRequest("custom", "NaN", 2, "数字", ""),
                    new AppConfigSaveRequest("custom", "TRUE", 3, "布尔", ""),
                    new AppConfigSaveRequest("custom", "{} {}", 4, "JSON", ""),
                    new AppConfigSaveRequest("custom", "", 4, "JSON", ""),
                    new AppConfigSaveRequest("custom", "x", 5, "错误类型", ""),
                    new AppConfigSaveRequest("miniapp.version", "  ", 1, "版本", ""),
                    new AppConfigSaveRequest("miniapp.version", "1", 2, "版本", ""),
                    new AppConfigSaveRequest("miniapp.home.title_image_url", "javascript:alert(1)", 1, "头图", ""),
                    new AppConfigSaveRequest("miniapp.home.title_image_url", "true", 3, "头图", "")}) {
                assertFalse(validator.validate(request).isEmpty(), request.toString());
            }
            assertEquals("https://example.com/a.png", new AppConfigSaveRequest("miniapp.home.title_image_url",
                    " HTTPS://example.com/a.png ", 1, "头图", null).configValue());
        }
    }

    @Test
    void duplicateKeyCannotOverwriteAndDeletedKeyCanBeRestored() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppConfigEntity.class);
        var mapper = mock(AppConfigMapper.class);
        var service = new AppConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var request = new AppConfigSaveRequest("miniapp.version", "1.2.3", 1, "版本", "");
        when(mapper.insert(any(AppConfigEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(BusinessException.class, () -> service.create(request));
        reset(mapper);
        when(mapper.restoreDeleted(any())).thenReturn(1);
        var restored = new AppConfigEntity();
        restored.setConfigValue("1.2.3");
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(restored);
        assertSame(restored, service.create(request));
        verify(mapper, never()).insert(any(AppConfigEntity.class));
        assertEquals("1.2.3", service.readMiniappConfig().version());
        restored.setConfigKey("miniapp.version");
        assertThrows(BusinessException.class, () -> service.update(1L,
                new AppConfigSaveRequest("changed.key", "1", 1, "版本", "")));
        verify(mapper, never()).updateById(any(AppConfigEntity.class));
        reset(mapper);
        assertEquals("", service.readMiniappConfig().version());
    }
}
