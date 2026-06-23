package com.rrs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrs.dto.CredentialCreateDTO;
import com.rrs.dto.CredentialDTO;
import com.rrs.entity.Credential;
import com.rrs.entity.CredentialType;
import com.rrs.exception.BusinessException;
import com.rrs.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    public List<CredentialDTO> listAll() {
        return credentialRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CredentialDTO> listByType(CredentialType type) {
        return credentialRepository.findByType(type).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CredentialDTO getById(Long id) {
        return toDTO(getEntity(id));
    }

    public Credential getEntity(Long id) {
        return credentialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Credential not found"));
    }

    @Transactional
    public CredentialDTO create(CredentialCreateDTO dto) {
        log.info("Creating credential: name={}, type={}", dto.getName(), dto.getType());
        Credential credential = new Credential();
        credential.setName(dto.getName());
        credential.setType(CredentialType.valueOf(dto.getType().toUpperCase()));
        credential.setParamsJson(toJson(dto.getParams()));
        credential = credentialRepository.save(credential);
        return toDTO(credential);
    }

    @Transactional
    public CredentialDTO update(Long id, CredentialCreateDTO dto) {
        log.info("Updating credential: id={}", id);
        Credential credential = getEntity(id);
        credential.setName(dto.getName());
        if (dto.getType() != null) {
            credential.setType(CredentialType.valueOf(dto.getType().toUpperCase()));
        }
        credential.setParamsJson(toJson(dto.getParams()));
        credential = credentialRepository.save(credential);
        return toDTO(credential);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting credential: id={}", id);
        credentialRepository.deleteById(id);
    }

    public Map<String, Object> getParams(Credential credential) {
        return fromJson(credential.getParamsJson());
    }

    // ---- helpers ----

    private CredentialDTO toDTO(Credential entity) {
        CredentialDTO dto = new CredentialDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setParams(fromJson(entity.getParamsJson()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "Invalid params JSON: " + e.getMessage());
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse params JSON", e);
            return Collections.emptyMap();
        }
    }
}
