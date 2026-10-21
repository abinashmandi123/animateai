package com.animateai.backend.service;

public class VideoGenerationException extends RuntimeException {

    public VideoGenerationException() {
        super("Video generation failed. Check the server logs for details.");
    }
}
