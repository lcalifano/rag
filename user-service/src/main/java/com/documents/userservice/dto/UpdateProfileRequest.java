package com.documents.userservice.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    private String username;

    @Email(message = "Email deve essere valida")
    private String email;

    /** Se presente, cambia la password. */
    private String newPassword;
}
