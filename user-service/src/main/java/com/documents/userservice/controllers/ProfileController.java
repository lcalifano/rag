package com.documents.userservice.controllers;

import com.documents.userservice.dto.UpdateProfileRequest;
import com.documents.userservice.dto.UserDto;
import com.documents.userservice.entities.User;
import com.documents.userservice.services.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final UserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<UserDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userManagementService.getProfile(user));
    }

    @PutMapping
    public ResponseEntity<UserDto> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userManagementService.updateProfile(user, request));
    }
}
