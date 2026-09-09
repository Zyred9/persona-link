package com.personalink.server.config;

import com.personalink.server.controller.app.MiniappReportAccessController;
import com.personalink.server.service.ReportAccessService;
import com.personalink.server.service.impl.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ConstructorInjectionTest {

    @Test
    void generatedConstructorPreservesSignatureAndSpringInjection() throws Exception {
        assertEquals(1, MiniappReportAccessController.class.getConstructors().length);
        MiniappReportAccessController.class.getConstructor(AuthService.class, ReportAccessService.class);
        AuthService authService = mock(AuthService.class);
        ReportAccessService accessService = mock(ReportAccessService.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AuthService.class, () -> authService);
            context.registerBean(ReportAccessService.class, () -> accessService);
            context.register(MiniappReportAccessController.class);
            context.refresh();
            MiniappReportAccessController controller = context.getBean(MiniappReportAccessController.class);
            assertSame(authService, ReflectionTestUtils.getField(controller, "authService"));
            assertSame(accessService, ReflectionTestUtils.getField(controller, "accessService"));
        }
    }
}
