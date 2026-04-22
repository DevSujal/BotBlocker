package com.assignment.backendengineering.dto.responseDTO;

import java.time.LocalDateTime;

public record PostResponseDTO(
        Long id,
        Long authorId,
        String authorType,
        String content,
        LocalDateTime createdAt
) {
}