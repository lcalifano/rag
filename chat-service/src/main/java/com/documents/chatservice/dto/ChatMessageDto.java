package com.documents.chatservice.dto;

import com.documents.chatservice.entities.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {
    private Long id;
    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;
}
