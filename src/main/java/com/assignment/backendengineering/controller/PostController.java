package com.assignment.backendengineering.controller;

import com.assignment.backendengineering.dto.requestDTO.CommentRequestDTO;
import com.assignment.backendengineering.dto.requestDTO.PostRequestDTO;
import com.assignment.backendengineering.dto.responseDTO.CommentResponseDTO;
import com.assignment.backendengineering.dto.responseDTO.PostResponseDTO;
import com.assignment.backendengineering.service.PostService;
import com.assignment.backendengineering.utils.AuthorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final StringRedisTemplate redisTemplate;

    @PostMapping
    public ResponseEntity<PostResponseDTO> createPost(@Valid @RequestBody PostRequestDTO request) {
        return ResponseEntity.ok(postService.createPost(request));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponseDTO> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequestDTO request) {
        return ResponseEntity.ok(postService.addComment(postId, request));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<Void> likePost(
            @PathVariable Long postId,
            @RequestParam String authorType) {
        postService.likePost(postId, AuthorType.valueOf(authorType.toUpperCase()));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}/virality")
    public ResponseEntity<Map<String, String>> getViralityScore(@PathVariable Long postId) {
        String scoreKey = "post:" + postId + ":virality_score";
        String countKey = "post:" + postId + ":bot_count";
        String score = redisTemplate.opsForValue().get(scoreKey);
        String botCount = redisTemplate.opsForValue().get(countKey);
        return ResponseEntity.ok(Map.of(
                "postId", postId.toString(),
                "viralityScore", score != null ? score : "0",
                "botReplyCount", botCount != null ? botCount : "0"
        ));
    }
}

