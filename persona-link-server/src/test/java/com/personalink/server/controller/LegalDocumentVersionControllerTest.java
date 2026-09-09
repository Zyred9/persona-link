package com.personalink.server.controller;

import com.personalink.server.controller.app.MiniappUserContentController;
import com.personalink.server.dto.LegalDocumentVersionResponse;
import com.personalink.server.service.FeedbackService;
import com.personalink.server.service.LegalDocumentService;
import com.personalink.server.service.TestRecordService;
import com.personalink.server.service.impl.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LegalDocumentVersionControllerTest {
    @Test
    void versionsIsPublicAndDoesNotMatchNumericDocumentRoute() throws Exception {
        var auth = mock(AuthService.class);
        var legal = mock(LegalDocumentService.class);
        when(legal.versions()).thenReturn(List.of(new LegalDocumentVersionResponse(1, 4)));
        var controller = new MiniappUserContentController(auth, mock(FeedbackService.class),
                legal, mock(TestRecordService.class));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/miniapp/legal-documents/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value(1))
                .andExpect(jsonPath("$.data[0].version").value(4))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());
        verifyNoInteractions(auth);
        verify(legal, never()).published(anyInt());
    }
}
