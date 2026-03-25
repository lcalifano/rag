package com.documents.userservice.dto;

import com.documents.userservice.validators.ModelProviderValidator;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmConfigDto {
//    @ModelProviderValidator
//    private String provider;

    private String modelName;
    private String embeddingModel;
//    private String ollamaUrl;
//    private String ollamaApi;
//    private String apiKey;
//    private String baseUrl;
    private Double temperature;
    private Boolean isActive;
}
