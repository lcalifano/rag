package com.documents.documentservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    @Value("${app.chunk-size:400}")
    private int chunkSize;

    @Value("${app.chunk-overlap:80}")
    private int chunkOverlap;

    public List<String> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            StringBuilder chunk = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) chunk.append(" ");
                chunk.append(words[i]);
            }
            chunks.add(chunk.toString());

            start += chunkSize - chunkOverlap;
            if (start >= words.length) break;
        }

        return chunks;
    }
}
