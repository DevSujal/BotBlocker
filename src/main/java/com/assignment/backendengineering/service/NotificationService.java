package com.assignment.backendengineering.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final StringRedisTemplate redisTemplate;

    private static final long NOTIF_COOLDOWN_MINUTES = 15;

    public void handleBotInteraction(Long userId, Long botId, String botName, Long postId) {
        String cooldownKey = "notif_cooldown:user_" + userId;
        String pendingKey = "user:" + userId + ":pending_notifs";
        String message = "Bot " + botName + " (id=" + botId + ") replied to your post (id=" + postId + ")";

        Boolean isFirstNotif = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", NOTIF_COOLDOWN_MINUTES, TimeUnit.MINUTES);

        if (Boolean.TRUE.equals(isFirstNotif)) {
            System.out.println("[NOTIFICATION] Push Notification Sent to User " + userId + ": " + message);
        } else {
            redisTemplate.opsForList().rightPush(pendingKey, message);
            System.out.println("[NOTIFICATION] User " + userId + " is in cooldown. Message buffered to pending list.");
        }
    }
}
