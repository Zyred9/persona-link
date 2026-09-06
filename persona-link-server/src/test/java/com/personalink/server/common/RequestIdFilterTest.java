package com.personalink.server.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestIdFilterTest {

    @Test
    void shouldReuseValidRequestIdAndGenerateMissingRequestId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest providedRequest = new MockHttpServletRequest();
        providedRequest.addHeader(RequestIdFilter.HEADER_NAME, "request-123");
        MockHttpServletResponse providedResponse = new MockHttpServletResponse();

        filter.doFilter(providedRequest, providedResponse, (request, response) -> { });
        assertEquals("request-123", providedResponse.getHeader(RequestIdFilter.HEADER_NAME));

        MockHttpServletResponse generatedResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), generatedResponse, (request, response) -> { });
        assertNotNull(generatedResponse.getHeader(RequestIdFilter.HEADER_NAME));
    }
}
