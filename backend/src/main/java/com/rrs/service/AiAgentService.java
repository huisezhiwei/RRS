package com.rrs.service;

import com.rrs.dto.AiAgentConfigDTO;
import com.rrs.dto.AiAgentDTO;
import com.rrs.entity.AgentType;
import com.rrs.entity.AiAgent;
import com.rrs.entity.Credential;
import com.rrs.entity.CredentialType;
import com.rrs.exception.BusinessException;
import com.rrs.repository.AiAgentRepository;
import com.rrs.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAgentService {

    private final AiAgentRepository aiAgentRepository;
    private final CredentialRepository credentialRepository;

    /**
     * Initialize built-in agents if they don't exist.
     */
    @Transactional
    public void initBuiltInAgents() {
        if (aiAgentRepository.findByCode("chat_assistant").isEmpty()) {
            AiAgent agent = new AiAgent();
            agent.setCode("chat_assistant");
            agent.setName("Chat Assistant");
            agent.setDescription("General-purpose chat assistant with LLM. Supports markdown rendering, file upload, and conversation history.");
            agent.setAgentType(AgentType.CHAT_ASSISTANT);
            agent.setTemperature(0.7);
            agent.setMaxTokens(4096);
            agent.setTopP(1.0);
            aiAgentRepository.save(agent);
            log.info("Initialized built-in agent: chat_assistant");
        }

        if (aiAgentRepository.findByCode("ocr_assistant").isEmpty()) {
            AiAgent ocrAgent = new AiAgent();
            ocrAgent.setCode("ocr_assistant");
            ocrAgent.setName("OCR 图片解析助手");
            ocrAgent.setDescription("识别图片中的文字信息，支持本地 Tesseract OCR 和大模型 Vision API，结果可保存为 Markdown 文件。");
            ocrAgent.setAgentType(AgentType.OCR_ASSISTANT);
            ocrAgent.setSystemPrompt("你是一个专业的 OCR 文字识别助手。请仔细识别图片中的所有文字内容，以 Markdown 格式输出，保留原始排版结构。");
            ocrAgent.setTemperature(0.1);
            ocrAgent.setMaxTokens(4096);
            ocrAgent.setTopP(1.0);
            aiAgentRepository.save(ocrAgent);
            log.info("Initialized built-in agent: ocr_assistant");
        }
    }

    public List<AiAgentDTO> listAll() {
        return aiAgentRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public AiAgentDTO getById(Long id) {
        return toDTO(getEntity(id));
    }

    public AiAgent getEntity(Long id) {
        return aiAgentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "AI Agent not found"));
    }

    @Transactional
    public AiAgentDTO configure(Long id, AiAgentConfigDTO config) {
        log.info("Configuring agent: id={}", id);
        AiAgent agent = getEntity(id);

        if (config.getCredentialId() != null) {
            Credential cred = credentialRepository.findById(config.getCredentialId())
                    .orElseThrow(() -> new BusinessException(404, "Credential not found"));
            if (cred.getType() != CredentialType.LLM) {
                throw new BusinessException(400, "Credential must be LLM type");
            }
            agent.setCredentialId(config.getCredentialId());
        }

        if (config.getModelName() != null) agent.setModelName(config.getModelName());
        if (config.getSystemPrompt() != null) agent.setSystemPrompt(config.getSystemPrompt());
        if (config.getTemperature() != null) agent.setTemperature(config.getTemperature());
        if (config.getMaxTokens() != null) agent.setMaxTokens(config.getMaxTokens());
        if (config.getTopP() != null) agent.setTopP(config.getTopP());

        agent = aiAgentRepository.save(agent);
        return toDTO(agent);
    }

    private AiAgentDTO toDTO(AiAgent entity) {
        AiAgentDTO dto = new AiAgentDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setAgentType(entity.getAgentType().name());
        dto.setCredentialId(entity.getCredentialId());
        dto.setModelName(entity.getModelName());
        dto.setSystemPrompt(entity.getSystemPrompt());
        dto.setTemperature(entity.getTemperature());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTopP(entity.getTopP());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Resolve credential name
        if (entity.getCredentialId() != null) {
            credentialRepository.findById(entity.getCredentialId())
                    .ifPresent(cred -> dto.setCredentialName(cred.getName()));
        }
        return dto;
    }
}
