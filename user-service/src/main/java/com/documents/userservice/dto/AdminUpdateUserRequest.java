package com.documents.userservice.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUpdateUserRequest {
    private String username;

    @Email(message = "Email deve essere valida")
    private String email;

    private String newPassword;

    /** Lista di nomi ruolo, es. ["ROLE_USER", "ROLE_ADMIN"] */
    private List<String> roles;
}
