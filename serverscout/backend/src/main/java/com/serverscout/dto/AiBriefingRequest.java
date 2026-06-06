package com.serverscout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiBriefingRequest(
        @NotBlank(message = "Evidence is required")
        @Size(max = 20000, message = "Evidence must not exceed 20000 characters")
        String evidence,
        String locale) {
}
