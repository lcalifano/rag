package com.documents.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkDto {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
}
