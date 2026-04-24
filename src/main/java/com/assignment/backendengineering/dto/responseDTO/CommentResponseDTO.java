package com.assignment.backendengineering.dto.responseDTO;

import java.time.LocalDateTime;

public record CommentResponseDTO(
        Long id,
        Long postId,
        Long authorId,
        String authorType,
        String content,
        Integer depthLevel,
        LocalDateTime createdAt
) {
}
