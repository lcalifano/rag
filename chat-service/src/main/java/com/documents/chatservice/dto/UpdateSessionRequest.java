package com.documents.chatservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSessionRequest {
    @NotBlank(message = "Il titolo non può essere vuoto")
    private String title;
}
