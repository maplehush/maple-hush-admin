package com.maplehush.admin.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    @Test
    void generatesTraceHeaderAndClearsLogContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "untrusted-client-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotEqualTo("untrusted-client-value");
        assertThat(MDC.get("traceId")).isNull();
    }
}
