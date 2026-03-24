package com.documents.userservice.entities;

import com.documents.userservice.dto.LlmConfigDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "llm_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    private String ollamaUrl;
    private String ollamaApi;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static LlmConfig fromDto(LlmConfigDto llmConfigDto) {
        return LlmConfig.builder()
                .provider(llmConfigDto.getProvider())
                .modelName(llmConfigDto.getModelName())
                .ollamaApi(llmConfigDto.getOllamaApi())
                .apiKey(llmConfigDto.getApiKey())
                .baseUrl(llmConfigDto.getBaseUrl())
                .temperature(llmConfigDto.getTemperature())
                .isActive(llmConfigDto.getIsActive())
                .ollamaUrl(llmConfigDto.getOllamaUrl())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
