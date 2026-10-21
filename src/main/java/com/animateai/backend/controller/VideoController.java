package com.animateai.backend.controller;

import java.util.Map;

import jakarta.validation.Valid;

import com.animateai.backend.service.VideoGenerationException;
import com.animateai.backend.service.VideoGenerationService;
import com.animateai.backend.service.VideoGenerationService.VideoGenerationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @PostMapping("/api/videos/text")
    public VideoGenerationResult generateTextVideo(@Valid @RequestBody TextVideoRequest request) {
        return videoGenerationService.generateTextVideo(request.text());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidText(MethodArgumentNotValidException ex) {
        return Map.of("status", "ERROR", "message",
                "text is required, must not be blank, and must contain at most 200 characters.");
    }

    @ExceptionHandler(VideoGenerationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGenerationFailure(VideoGenerationException ex) {
        return Map.of("status", "ERROR", "message", ex.getMessage());
    }
}
