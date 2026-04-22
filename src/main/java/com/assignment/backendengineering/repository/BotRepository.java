package com.assignment.backendengineering.repository;

import com.assignment.backendengineering.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot, Long> {
}
