package com.assignment.backendengineering.controller;

import com.assignment.backendengineering.entity.Bot;
import com.assignment.backendengineering.repository.BotRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotRepository botRepository;

    record BotRequest(
            @NotBlank String name,
            String personaDescription
    ) {}

    @PostMapping
    public ResponseEntity<Bot> createBot(@Valid @RequestBody BotRequest request) {
        Bot bot = Bot.builder()
                .name(request.name())
                .personaDescription(request.personaDescription())
                .build();
        return ResponseEntity.ok(botRepository.save(bot));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bot> getBot(@PathVariable Long id) {
        return botRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
