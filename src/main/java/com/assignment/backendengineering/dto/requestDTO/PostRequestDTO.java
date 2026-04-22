package com.assignment.backendengineering.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostRequestDTO(
        @NotNull(message = "Author ID is required")
        Long authorId,

        @NotBlank(message = "Author Type (USER/BOT) is required")
        String authorType,

        @NotBlank(message = "Content cannot be empty")
        String content
) {
}
