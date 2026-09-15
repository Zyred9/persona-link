package com.personalink.server.service.impl;

import com.personalink.server.dto.HomeConfigSaveRequest;
import com.personalink.server.dto.AppConfigSaveRequest;
import com.personalink.server.entity.AppConfigEntity;
import com.personalink.server.mapper.AppConfigMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.personalink.server.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppConfigServiceImplTest {
    @Test
    void readsPublicValuesInOneFilteredQueryAndRejectsPrivateKeys() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppConfigEntity.class);
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        AppConfigServiceImpl service = new AppConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var row = new AppConfigEntity();
        row.setConfigKey("miniapp.version");
        row.setConfigValue("1.2.3");
        var emailRow = new AppConfigEntity();
        emailRow.setConfigKey("miniapp.contact_email");
        emailRow.setConfigValue("support@example.com");
        when(mapper.selectList(any())).thenReturn(List.of(row, emailRow));
        var values = service.readPublicValues(List.of("miniapp.version", "miniapp.contact_email", "miniapp.pair.waiting_hero_image_url"));
        assertEquals("1.2.3", values.get("miniapp.version"));
        assertEquals("support@example.com", values.get("miniapp.contact_email"));
        assertEquals("", values.get("miniapp.pair.waiting_hero_image_url"));
        var wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("config_key IN"));
        assertTrue(wrapper.getValue().getSqlSegment().contains("deleted ="));
        assertThrows(BusinessException.class, () -> service.readPublicValues(List.of("server.secret")));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void validatesProgressHeroConfigUrls() {
        for (String key : List.of("miniapp.pair.waiting_hero_image_url", "miniapp.pair.completed_hero_image_url")) {
            var valid = new AppConfigSaveRequest(key, " HTTPS://example.com/progress.png ", 1, "头图", "");
            assertEquals("https://example.com/progress.png", valid.configValue());
            assertTrue(valid.isBusinessValueValid());
            assertTrue(new AppConfigSaveRequest(key, "", 1, "头图", "").isBusinessValueValid());
            assertFalse(new AppConfigSaveRequest(key, "javascript:alert(1)", 1, "头图", "").isBusinessValueValid());
            assertFalse(new AppConfigSaveRequest(key, "123", 2, "头图", "").isBusinessValueValid());
        }
    }

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
    void readsPairHeroKeysWithEmptyFallback() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppConfigEntity.class);
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        AppConfigServiceImpl service = new AppConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        assertEquals("", service.readPairConfig().joinHeroImageUrl());
        assertEquals("", service.readPairConfig().waitingHeroImageUrl());
        assertEquals("", service.readPairConfig().completedHeroImageUrl());
        AppConfigEntity row = new AppConfigEntity();
        row.setConfigKey("miniapp.pair.join_hero_image_url");
        row.setConfigValue("https://example.com/pair.png");
        AppConfigEntity waiting = new AppConfigEntity();
        waiting.setConfigKey("miniapp.pair.waiting_hero_image_url");
        waiting.setConfigValue("https://example.com/waiting.png");
        AppConfigEntity completed = new AppConfigEntity();
        completed.setConfigKey("miniapp.pair.completed_hero_image_url");
        completed.setConfigValue("https://example.com/completed.png");
        when(mapper.selectList(any())).thenReturn(List.of(row, waiting, completed));
        var response = service.readPairConfig();
        assertEquals(row.getConfigValue(), response.joinHeroImageUrl());
        assertEquals(waiting.getConfigValue(), response.waitingHeroImageUrl());
        assertEquals(completed.getConfigValue(), response.completedHeroImageUrl());
        completed.setConfigValue(null);
        assertEquals("", service.readPairConfig().completedHeroImageUrl());
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
