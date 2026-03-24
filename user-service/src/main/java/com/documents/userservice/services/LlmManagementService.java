package com.documents.userservice.services;

import com.documents.userservice.dto.LlmConfigDto;
import com.documents.userservice.entities.LlmConfig;
import com.documents.userservice.entities.User;
import com.documents.userservice.exceptions.ProviderAlreadyExistsException;
import com.documents.userservice.repositories.LlmRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LlmManagementService {

    private final LlmRepository llmRepository;

    @Transactional
    public LlmConfig save(LlmConfigDto llmConfigDto, User user) {
        Boolean model = llmRepository.existsByUserIdAndProvider(user.getId(), llmConfigDto.getProvider());
        if (model) {
            throw new ProviderAlreadyExistsException(llmConfigDto.getProvider());
        }
        LlmConfig llmconfig = LlmConfig.fromDto(llmConfigDto);
        llmconfig.setUserId(user.getId());
        return llmRepository.save(llmconfig);
    }

    public List<LlmConfig> getAllLlmConfigs() {
        return llmRepository.findAll();
    }

    public List<LlmConfig> getUserConfigs(User user) {
        return llmRepository.findAllByUserId(user.getId());
    }

    public Optional<LlmConfig> getActiveLlmConfig(Long userId) {
        return llmRepository.findByUserIdAndIsActiveTrue(userId);
    }
}
