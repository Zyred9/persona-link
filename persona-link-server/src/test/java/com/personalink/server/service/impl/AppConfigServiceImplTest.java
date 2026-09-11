package com.personalink.server.service.impl;

import com.personalink.server.dto.HomeConfigSaveRequest;
import com.personalink.server.entity.AppConfigEntity;
import com.personalink.server.mapper.AppConfigMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppConfigServiceImplTest {
    @Test
    void validatesImageAddressAtRequestBoundary() {
        assertEquals("https://example.com/title.png", new HomeConfigSaveRequest("HTTPS://example.com/title.png").titleImageUrl());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String url : new String[]{null, "", "  ", "https://example.com/title.png?a=1", "http://localhost/title.png", "/uploads/title.png"}) {
                assertTrue(validator.validate(new HomeConfigSaveRequest(url)).isEmpty(), String.valueOf(url));
            }
            for (String url : new String[]{"javascript:alert(1)", "//evil.com/image.png", "https:///a", "https://user:pass@example.com/a", "/uploads/../secret.png", "https://example.com/" + "x".repeat(1024)}) {
                assertFalse(validator.validate(new HomeConfigSaveRequest(url)).isEmpty(), url);
            }
        }
    }

    @Test
    void readsPairJoinHeroKeyWithEmptyFallback() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppConfigEntity.class);
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        AppConfigServiceImpl service = new AppConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        assertEquals("", service.readPairConfig().joinHeroImageUrl());
        AppConfigEntity row = new AppConfigEntity();
        row.setConfigValue("https://example.com/pair.png");
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(row);
        assertEquals(row.getConfigValue(), service.readPairConfig().joinHeroImageUrl());
    }

    @Test
    void readsOnlyHomeKeyAndSavesNormalizedValueIncludingClear() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppConfigEntity.class);
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        AppConfigServiceImpl service = new AppConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        assertEquals("", service.readHomeConfig().titleImageUrl());
        AppConfigEntity row = new AppConfigEntity();
        row.setConfigValue("https://example.com/title.png");
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(row);
        assertEquals(row.getConfigValue(), service.readHomeConfig().titleImageUrl());
        assertEquals("https://example.com/title.png", service.saveHomeConfig(new HomeConfigSaveRequest(" https://example.com/title.png ")).titleImageUrl());
        assertEquals("", service.saveHomeConfig(new HomeConfigSaveRequest(null)).titleImageUrl());
        var saved = ArgumentCaptor.forClass(AppConfigEntity.class);
        verify(mapper, times(2)).upsert(saved.capture());
        assertEquals("miniapp.home.title_image_url", saved.getValue().getConfigKey());
        assertEquals(1, saved.getValue().getValueType());
        assertEquals("", saved.getValue().getConfigValue());
    }
}
