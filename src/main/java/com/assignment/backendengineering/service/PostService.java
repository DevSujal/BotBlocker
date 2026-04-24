package com.assignment.backendengineering.service;

import com.assignment.backendengineering.dto.requestDTO.CommentRequestDTO;
import com.assignment.backendengineering.dto.requestDTO.PostRequestDTO;
import com.assignment.backendengineering.dto.responseDTO.CommentResponseDTO;
import com.assignment.backendengineering.dto.responseDTO.PostResponseDTO;
import com.assignment.backendengineering.entity.Comment;
import com.assignment.backendengineering.entity.Post;
import com.assignment.backendengineering.repository.CommentRepository;
import com.assignment.backendengineering.repository.PostRepository;
import com.assignment.backendengineering.utils.AuthorType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PostService {

    public final PostRepository postRepository;
    public final CommentRepository commentRepository;
    public final StringRedisTemplate redisTemplate;

    private Post convertToPost(PostRequestDTO request) {
        AuthorType type;
        try {
            type = getAuthorTypeByString(request.authorType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Author Type. Use USER or BOT.");
        }


        return Post.builder().authorId(request.authorId()).authorType(type).content(request.content()).build();
    }

    private AuthorType getAuthorTypeByString(String authorType) {
        AuthorType type;
        try {
            type = AuthorType.valueOf(authorType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Author Type. Use USER or BOT.");
        }

        return type;
    }

    private PostResponseDTO convertToPostDTO(Post post) {

        return new PostResponseDTO(post.getId(), post.getAuthorId(), post.getAuthorType().toString(), post.getContent(), post.getCreatedAt());
    }

    private CommentResponseDTO convertToCommentResponseDTO(Comment comment) {

        return new CommentResponseDTO(comment.getId(), comment.getPost().getId(), comment.getAuthorId(), comment.getAuthorType().toString(), comment.getContent(), comment.getDepthLevel(), comment.getCreatedAt());
    }

    @Transactional
    public PostResponseDTO createPost(PostRequestDTO request) {
        Post post = convertToPost(request);
        Post savedPost = postRepository.save(post);

        return convertToPostDTO(savedPost);
    }

    @Transactional
    public CommentResponseDTO addComment(Long postId, CommentRequestDTO request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        AuthorType authorType = getAuthorTypeByString(request.authorType());

//        // 1. PHASE 2: ATOMIC LOCKS & GUARDRAILS (For Bots)
//        if (authorType == AuthorType.BOT) {
//            checkBotGuardrails(postId, request.authorId(), post.getAuthorId());
//        }

//        // 2. Logic for Depth Level (Simplified for this step)
        int depthLevel = 1;
//        if (depthLevel > 20) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vertical Cap: Thread too deep");
//        }

        Comment comment = Comment.builder()
                .post(post)
                .authorId(request.authorId())
                .authorType(authorType)
                .content(request.content())
                .depthLevel(depthLevel)
                .build();

//        // 3. PHASE 2: VIRALITY ENGINE
//        updateViralityScore(postId, authorType, "COMMENT");

        return convertToCommentResponseDTO(commentRepository.save(comment));
    }

    public void likePost(Long postId, AuthorType type) {
        // Ensure post exists before liking
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        updateViralityScore(postId, type, "LIKE");
    }


    private void updateViralityScore(Long postId, AuthorType type, String action) {
        String key = "post:" + postId + ":virality_score";
        int points = 0;

        if (type == AuthorType.BOT && action.equals("COMMENT")) points = 1;
        else if (type == AuthorType.USER && action.equals("LIKE")) points = 20;
        else if (type == AuthorType.USER && action.equals("COMMENT")) points = 50;

        System.out.println(key + ", " + points + ", " + type);

        if (points > 0) {
            redisTemplate.opsForValue().increment(key, points);
        }
    }

    private void checkBotGuardrails(Long postId, Long botId, Long postOwnerId) {
        // Horizontal Cap: Max 100 bot replies per post
        String countKey = "post:" + postId + ":bot_count";
        Long currentCount = redisTemplate.opsForValue().increment(countKey);

        if (currentCount != null && currentCount > 100) {
            // Rollback the increment if we exceed the limit
            redisTemplate.opsForValue().decrement(countKey);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Horizontal Cap: 100 bot replies reached");
        }

        // Cooldown Cap: 1 bot vs 1 human per 10 minutes
        String cooldownKey = "cooldown:bot_" + botId + ":human_" + postOwnerId;
        Boolean canInteract = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "active", 10, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(canInteract)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cooldown Cap: Bot on 10-minute cooldown");
        }
    }
}
