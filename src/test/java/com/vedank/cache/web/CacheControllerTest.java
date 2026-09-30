package com.vedank.cache.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CacheApplication.class)
@AutoConfigureMockMvc
class CacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void putThenGetRoundTripsThroughRest() throws Exception {
        mockMvc.perform(put("/api/cache/greeting")
                        .contentType("application/json")
                        .content("{\"value\":\"hello\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cache/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("greeting"))
                .andExpect(jsonPath("$.value").value("hello"));
    }

    @Test
    void getMissingKeyReturns404() throws Exception {
        mockMvc.perform(get("/api/cache/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesKey() throws Exception {
        mockMvc.perform(put("/api/cache/temp").contentType("application/json").content("{\"value\":\"x\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/cache/temp")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cache/temp")).andExpect(status().isNotFound());
    }

    @Test
    void sizeAndMetricsEndpointsRespond() throws Exception {
        mockMvc.perform(put("/api/cache/m1").contentType("application/json").content("{\"value\":1}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cache/size"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").isNumber());

        mockMvc.perform(get("/api/cache/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").isNumber())
                .andExpect(jsonPath("$.hitRate").isNumber());
    }

    @Test
    void clearEndpointEmptiesTheCache() throws Exception {
        mockMvc.perform(put("/api/cache/c1").contentType("application/json").content("{\"value\":1}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/cache/clear")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cache/c1")).andExpect(status().isNotFound());
    }
}
