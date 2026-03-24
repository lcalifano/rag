package com.documents.userservice.controllers;

import com.documents.userservice.dto.LlmConfigDto;
import com.documents.userservice.entities.LlmConfig;
import com.documents.userservice.services.LlmManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final LlmManagementService llmManagementService;

    @GetMapping("/users/{userId}/active-llm-config")
    public ResponseEntity<LlmConfig> getActiveLlmConfig(@PathVariable Long userId) {
        return llmManagementService.getActiveLlmConfig(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
