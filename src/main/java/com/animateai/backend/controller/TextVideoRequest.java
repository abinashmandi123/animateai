package com.animateai.backend.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextVideoRequest(
        @NotBlank(message = "text is required and must not be blank")
        @Size(max = 200, message = "text must contain at most 200 characters")
        String text) {
}
