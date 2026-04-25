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

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final StringRedisTemplate redisTemplate;
    private final BotGuardrailService botGuardrailService;

    @Transactional
    public PostResponseDTO createPost(PostRequestDTO request) {
        AuthorType authorType = parseAuthorType(request.authorType());
        Post post = Post.builder()
                .authorId(request.authorId())
                .authorType(authorType)
                .content(request.content())
                .build();

        return toPostResponse(postRepository.save(post));
    }

    @Transactional
    public CommentResponseDTO addComment(Long postId, CommentRequestDTO request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        AuthorType authorType = parseAuthorType(request.authorType());

        int depthLevel = resolveDepthLevel(request.parentCommentId());

        if (authorType == AuthorType.BOT) {
            botGuardrailService.enforceVerticalCap(depthLevel);
            botGuardrailService.enforceHorizontalCap(postId);
            botGuardrailService.enforceCooldownCap(request.authorId(), post.getAuthorId());
        }

        Comment comment = Comment.builder()
                .post(post)
                .authorId(request.authorId())
                .authorType(authorType)
                .content(request.content())
                .depthLevel(depthLevel)
                .build();

        commentRepository.save(comment);
        updateViralityScore(postId, authorType);

        return toCommentResponse(comment);
    }

    public void likePost(Long postId, AuthorType authorType) {
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        if (authorType == AuthorType.USER) {
            String key = "post:" + postId + ":virality_score";
            redisTemplate.opsForValue().increment(key, 20);
        }
    }

    private int resolveDepthLevel(Long parentCommentId) {
        if (parentCommentId == null) {
            return 1;
        }
        Comment parent = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent comment not found"));

        return parent.getDepthLevel() + 1;
    }

    private void updateViralityScore(Long postId, AuthorType authorType) {
        String key = "post:" + postId + ":virality_score";
        if (authorType == AuthorType.BOT) {
            redisTemplate.opsForValue().increment(key, 1);
        } else if (authorType == AuthorType.USER) {
            redisTemplate.opsForValue().increment(key, 50);
        }
    }

    private AuthorType parseAuthorType(String raw) {
        try {
            return AuthorType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Author Type. Use USER or BOT.");
        }
    }

    private PostResponseDTO toPostResponse(Post post) {
        return new PostResponseDTO(
                post.getId(),
                post.getAuthorId(),
                post.getAuthorType().toString(),
                post.getContent(),
                post.getCreatedAt()
        );
    }

    private CommentResponseDTO toCommentResponse(Comment comment) {
        return new CommentResponseDTO(
                comment.getId(),
                comment.getPost().getId(),
                comment.getAuthorId(),
                comment.getAuthorType().toString(),
                comment.getContent(),
                comment.getDepthLevel(),
                comment.getCreatedAt()
        );
    }
}

