package com.assignment.backendengineering.service;

import com.assignment.backendengineering.dto.requestDTO.PostRequestDTO;
import com.assignment.backendengineering.dto.responseDTO.PostResponseDTO;
import com.assignment.backendengineering.entity.Post;
import com.assignment.backendengineering.repository.PostRepository;
import com.assignment.backendengineering.utils.AuthorType;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    private Post convertToPost(PostRequestDTO request) {
        AuthorType type;
        try {
            type = AuthorType.valueOf(request.authorType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Author Type. Use USER or BOT.");
        }


        return Post.builder().authorId(request.authorId()).authorType(type).content(request.content()).build();
    }

    private PostResponseDTO convertToPostDTO(Post post) {

        return new PostResponseDTO(post.getId(), post.getAuthorId(), post.getAuthorType().toString(), post.getContent(), post.getCreatedAt());
    }

    @Transactional
    public PostResponseDTO createPost(PostRequestDTO request) {
        Post post = convertToPost(request);
        Post savedPost = postRepository.save(post);

        return convertToPostDTO(savedPost);
    }
}
