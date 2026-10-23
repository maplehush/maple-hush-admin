package com.maplehush.admin.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    @RestController
    static class Endpoint {
        @GetMapping("/probe/{id}")
        public String getValue(@PathVariable("id") Long id) {
            return id.toString();
        }
    }

    @Test
    void unsupportedMethodReturns405AndAllowHeader() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(post("/probe/1")).andReturn().getResponse();
        assertEquals(405, response.getStatus());
        assertTrue(response.getHeader("Allow").contains("GET"));
        assertTrue(response.getContentAsString().contains("BAD_REQUEST"));
    }

    @Test
    void invalidPathParameterReturns400() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(get("/probe/not-a-number")).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_ARGUMENT"));
    }
}
