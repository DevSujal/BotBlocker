package com.assignment.backendengineering.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BotGuardrailService {

    private final StringRedisTemplate redisTemplate;

    private static final int HORIZONTAL_CAP = 100;
    private static final int VERTICAL_CAP = 20;
    private static final long COOLDOWN_MINUTES = 10;

    private static final DefaultRedisScript<Long> ATOMIC_INCREMENT_WITH_CAP = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current > tonumber(ARGV[1]) then
                redis.call('DECR', KEYS[1])
                return -1
            end
            return current
            """,
            Long.class
    );

    public void enforceHorizontalCap(Long postId) {
        String countKey = "post:" + postId + ":bot_count";
        Long result = redisTemplate.execute(
                ATOMIC_INCREMENT_WITH_CAP,
                Collections.singletonList(countKey),
                String.valueOf(HORIZONTAL_CAP)
        );

        if (result == null || result == -1L) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Horizontal Cap: This post has reached the maximum of 100 bot replies."
            );
        }
    }

    public void enforceVerticalCap(int depthLevel) {
        if (depthLevel > VERTICAL_CAP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vertical Cap: Comment thread cannot exceed 20 levels deep."
            );
        }
    }

    public void enforceCooldownCap(Long botId, Long postOwnerId) {
        String cooldownKey = "cooldown:bot_" + botId + ":human_" + postOwnerId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", COOLDOWN_MINUTES, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(acquired)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Cooldown Cap: This bot must wait 10 minutes before interacting with this user again."
            );
        }
    }
}
