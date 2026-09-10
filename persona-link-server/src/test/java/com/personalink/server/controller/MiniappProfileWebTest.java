package com.personalink.server.controller;

import com.personalink.server.controller.app.MiniappProfileController;
import com.personalink.server.dto.MiniappProfileResponse;
import com.personalink.server.dto.MiniappSessionContext;
import com.personalink.server.exception.GlobalExceptionHandler;
import com.personalink.server.miniapp.auth.WechatCode2SessionClient;
import com.personalink.server.service.BusinessSessionService;
import com.personalink.server.service.MiniappUserService;
import com.personalink.server.service.impl.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MiniappProfileWebTest {
    @Test
    void anonymousReadWriteAndUploadRejectBeforeProfileAccess() throws Exception {
        MiniappUserService users = mock(MiniappUserService.class);
        AuthService auth = new AuthService(mock(WechatCode2SessionClient.class), mock(BusinessSessionService.class), users);
        var mvc = MockMvcBuilders.standaloneSetup(new MiniappProfileController(auth, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/miniapp/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/miniapp/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\",\"avatarUrl\":\"\"}")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/miniapp/profile/avatar").file("file", new byte[]{1}))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void bindsIdentityFromSessionAndValidatesNicknameLength() throws Exception {
        MiniappUserService users = mock(MiniappUserService.class);
        AuthService auth = mock(AuthService.class);
        when(auth.requireSession("Bearer valid")).thenReturn(new MiniappSessionContext("real-openid", null));
        when(users.profile("real-openid")).thenReturn(new MiniappProfileResponse("昵称", ""));
        var mvc = MockMvcBuilders.standaloneSetup(new MiniappProfileController(auth, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/miniapp/profile").header("Authorization", "Bearer valid").param("openId", "forged"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("昵称"));
        mvc.perform(put("/api/miniapp/profile").header("Authorization", "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + "a".repeat(33) + "\",\"avatarUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).updateProfile(anyString(), any());
    }
}
