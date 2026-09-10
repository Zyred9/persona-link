package com.personalink.server.controller;

import com.personalink.server.controller.admin.AdminAppConfigController;
import com.personalink.server.controller.app.MiniappConfigController;
import com.personalink.server.dto.MiniappConfigResponse;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.exception.GlobalExceptionHandler;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.AdminAuthService;
import com.personalink.server.service.AppConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppConfigWebTest {
    @Test
    void publicConfigOnlyExposesVersionWithoutLogin() throws Exception {
        var service = mock(AppConfigService.class);
        when(service.readMiniappConfig()).thenReturn(new MiniappConfigResponse("1.2.3"));
        var mvc = MockMvcBuilders.standaloneSetup(new MiniappConfigController(service)).build();
        mvc.perform(get("/api/miniapp/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("1.2.3"))
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(service).readMiniappConfig();
        verifyNoMoreInteractions(service);
    }

    @Test
    void managementRejectsNonAdminAndInvalidValuesBeforeWriting() throws Exception {
        var service = mock(AppConfigService.class);
        var auth = mock(AdminAuthService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AdminAuthService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(auth);
        when(auth.requireAdminSession("Bearer operator"))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, 403, "需要管理员权限"));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminAppConfigController(service))
                .addInterceptors(new AdminAuthInterceptor(provider))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/admin/app-configs").header("Authorization", "Bearer operator"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/app-configs").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configKey":"miniapp.version","configValue":"","valueType":1,"configName":"版本"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/app-configs").header("Authorization", "Bearer admin").param("size", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
