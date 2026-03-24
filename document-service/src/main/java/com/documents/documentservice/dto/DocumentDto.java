package com.documents.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDto {
    private Long id;
    private String originalFilename;
    private Long fileSize;
    private Integer totalChunks;
    private String status;
    private LocalDateTime createdAt;
}
