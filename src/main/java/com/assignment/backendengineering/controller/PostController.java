package com.assignment.backendengineering.controller;

import com.assignment.backendengineering.dto.requestDTO.CommentRequestDTO;
import com.assignment.backendengineering.dto.requestDTO.PostRequestDTO;
import com.assignment.backendengineering.dto.responseDTO.CommentResponseDTO;
import com.assignment.backendengineering.dto.responseDTO.PostResponseDTO;
import com.assignment.backendengineering.service.PostService;
import com.assignment.backendengineering.utils.AuthorType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @PostMapping
    public ResponseEntity<PostResponseDTO> createPost(@Valid @RequestBody PostRequestDTO request) {
        return ResponseEntity.ok(postService.createPost(request));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponseDTO> addComment(@PathVariable Long postId, @Valid @RequestBody CommentRequestDTO request) {

        return ResponseEntity.ok(postService.addComment(postId, request));
    }
    
    @PostMapping("/{postId}/like")
    public ResponseEntity<Void> likePost(
            @PathVariable Long postId,
            @RequestParam String authorType) {

        // Convert String to Enum
        AuthorType type = AuthorType.valueOf(authorType.toUpperCase());

        // Process the like logic (updates Redis Virality Score)
        postService.likePost(postId, type);

        return ResponseEntity.ok().build();
    }
}
