package com.assignment.backendengineering.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotificationSweeper {

    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 300000)
    public void sweepPendingNotifications() {
        System.out.println("[SWEEPER] Running notification sweep...");

        Set<String> pendingKeys = redisTemplate.keys("user:*:pending_notifs");
        if (pendingKeys == null || pendingKeys.isEmpty()) {
            System.out.println("[SWEEPER] No pending notifications found.");
            return;
        }

        for (String key : pendingKeys) {
            Long listSize = redisTemplate.opsForList().size(key);
            if (listSize == null || listSize == 0) {
                continue;
            }

            List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
            redisTemplate.delete(key);

            if (messages == null || messages.isEmpty()) {
                continue;
            }

            String userId = key.replace("user:", "").replace(":pending_notifs", "");
            String firstMessage = messages.get(0);

            String botName = extractBotName(firstMessage);
            int othersCount = messages.size() - 1;

            if (othersCount == 0) {
                System.out.println("[SWEEPER] Summarized Push Notification to User " + userId + ": " + botName + " interacted with your posts.");
            } else {
                System.out.println("[SWEEPER] Summarized Push Notification to User " + userId + ": " + botName + " and [" + othersCount + "] others interacted with your posts.");
            }
        }
    }

    private String extractBotName(String message) {
        try {
            return message.substring(4, message.indexOf("(")).trim();
        } catch (Exception e) {
            return "A bot";
        }
    }
}
