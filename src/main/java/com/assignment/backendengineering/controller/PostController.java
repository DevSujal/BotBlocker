package com.assignment.backendengineering.controller;

import com.assignment.backendengineering.dto.requestDTO.PostRequestDTO;
import com.assignment.backendengineering.dto.responseDTO.PostResponseDTO;
import com.assignment.backendengineering.service.PostService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @PostMapping
    public ResponseEntity<PostResponseDTO> createPost(@Valid @RequestBody PostRequestDTO request) {
        return ResponseEntity.ok(postService.createPost(request));
    }
}
