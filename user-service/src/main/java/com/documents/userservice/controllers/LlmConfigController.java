package com.documents.userservice.controllers;

import com.documents.userservice.dto.LlmConfigDto;
import com.documents.userservice.entities.LlmConfig;
import com.documents.userservice.entities.User;
import com.documents.userservice.services.LlmManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/model/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class LlmConfigController {

    private final LlmManagementService llmManagementService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LlmConfig>> getAllLlmConfigs() {
        return ResponseEntity.status(HttpStatus.OK).body(llmManagementService.getAllLlmConfigs());
    }

    @GetMapping("/my")
    public ResponseEntity<List<LlmConfig>> getMyLlmConfigs(@AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.OK).body(llmManagementService.getUserConfigs(user));
    }

    @PostMapping("/")
    public ResponseEntity<?> saveConfig(@Valid @RequestBody LlmConfigDto request,
                                        @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(llmManagementService.save(request, user));
    }
}
