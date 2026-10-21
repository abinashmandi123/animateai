package com.animateai.backend.controller;

import com.animateai.backend.service.VideoGenerationException;
import com.animateai.backend.service.VideoGenerationService;
import com.animateai.backend.service.VideoGenerationService.VideoGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VideoControllerTest {

    private VideoGenerationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(VideoGenerationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new VideoController(service)).build();
    }

    @Test
    void returnsGeneratedVideoAsJson() throws Exception {
        when(service.generateTestVideo()).thenReturn(new VideoGenerationResult(
                "SUCCESS", "test-example.mp4", "generated-videos/test-example.mp4"));
        mvc.perform(post("/api/videos/test"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.fileName").value("test-example.mp4"))
                .andExpect(jsonPath("$.path").value("generated-videos/test-example.mp4"));
    }

    @Test
    void returnsSafeServerErrorOnGenerationFailure() throws Exception {
        when(service.generateTestVideo()).thenThrow(new VideoGenerationException());
        mvc.perform(post("/api/videos/test"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Video generation failed. Check the server logs for details."));
    }
}
