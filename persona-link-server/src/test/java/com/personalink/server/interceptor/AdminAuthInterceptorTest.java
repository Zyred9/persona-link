package com.personalink.server.interceptor;

import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.service.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthInterceptorTest {

    @Test
    void shouldRequireReadSessionForGetAndWritableSessionForPost() {
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AdminAuthService> provider = mock(ObjectProvider.class);
        AdminSessionContext context = mock(AdminSessionContext.class);
        when(provider.getObject()).thenReturn(adminAuthService);
        when(adminAuthService.requireSession("Bearer read-token")).thenReturn(context);
        when(adminAuthService.requireWritableSession("Bearer write-token")).thenReturn(context);
        when(adminAuthService.requireAdminSession("Bearer admin-token")).thenReturn(context);
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(provider);

        MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/admin/categories");
        getRequest.addHeader("Authorization", "Bearer read-token");
        assertTrue(interceptor.preHandle(getRequest, new MockHttpServletResponse(), new Object()));
        assertSame(context, getRequest.getAttribute(AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE));

        MockHttpServletRequest postRequest = new MockHttpServletRequest("POST", "/api/admin/categories");
        postRequest.addHeader("Authorization", "Bearer write-token");
        assertTrue(interceptor.preHandle(postRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest accountRequest = new MockHttpServletRequest("GET", "/api/admin/accounts");
        accountRequest.addHeader("Authorization", "Bearer admin-token");
        assertTrue(interceptor.preHandle(accountRequest, new MockHttpServletResponse(), new Object()));

        verify(adminAuthService).requireSession("Bearer read-token");
        verify(adminAuthService).requireWritableSession("Bearer write-token");
        verify(adminAuthService).requireAdminSession("Bearer admin-token");
    }
}
