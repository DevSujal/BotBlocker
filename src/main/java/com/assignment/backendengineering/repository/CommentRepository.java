package com.assignment.backendengineering.repository;

import com.assignment.backendengineering.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}

