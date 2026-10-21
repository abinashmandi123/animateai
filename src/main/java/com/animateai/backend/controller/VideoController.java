package com.animateai.backend.controller;

import java.util.Map;

import com.animateai.backend.service.VideoGenerationException;
import com.animateai.backend.service.VideoGenerationService;
import com.animateai.backend.service.VideoGenerationService.VideoGenerationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VideoController {

    private final VideoGenerationService videoGenerationService;

    public VideoController(VideoGenerationService videoGenerationService) {
        this.videoGenerationService = videoGenerationService;
    }

    @PostMapping("/api/videos/test")
    public VideoGenerationResult generateTestVideo() {
        return videoGenerationService.generateTestVideo();
    }

    @ExceptionHandler(VideoGenerationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGenerationFailure(VideoGenerationException ex) {
        return Map.of("status", "ERROR", "message", ex.getMessage());
    }
}
