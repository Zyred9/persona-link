package com.personalink.server.controller;

import com.personalink.server.aspect.ControllerLogAspect;
import com.personalink.server.config.AssetWebMvcConfiguration;
import com.personalink.server.controller.app.MiniappPairController;
import com.personalink.server.miniapp.auth.WechatCode2SessionClient;
import com.personalink.server.service.BusinessSessionService;
import com.personalink.server.service.PairAssessmentService;
import com.personalink.server.service.impl.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 覆盖真实请求绑定、方法校验与日志 AOP，避免只测无代理 Controller。 */
@WebMvcTest(controllers = MiniappPairController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = AssetWebMvcConfiguration.class))
@Import({AopAutoConfiguration.class, ControllerLogAspect.class, AuthService.class})
class MiniappPairAuthWebTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PairAssessmentService pairAssessmentService;
    @MockitoBean
    private BusinessSessionService businessSessionService;
    @MockitoBean
    private WechatCode2SessionClient wechatCode2SessionClient;

    @Test
    void anonymousPairHistoryAndDetailReturnUnauthorizedWithoutDatabaseAccess() throws Exception {
        for (String path : new String[]{"/api/miniapp/pairs", "/api/miniapp/pairs/1"}) {
            this.mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40102));
        }
        verifyNoInteractions(this.pairAssessmentService, this.businessSessionService, this.wechatCode2SessionClient);
    }
}
