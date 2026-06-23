package com.rrs.service;

import com.rrs.dto.TagDTO;
import com.rrs.entity.Tag;
import com.rrs.exception.BusinessException;
import com.rrs.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagDTO> listAll() {
        return tagRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TagDTO create(TagDTO dto) {
        if (tagRepository.findByName(dto.getName()).isPresent()) {
            throw new BusinessException("标签名称已存在: " + dto.getName());
        }

        Tag tag = new Tag();
        tag.setName(dto.getName());
        tag.setColor(dto.getColor());

        tag = tagRepository.save(tag);
        return toDTO(tag);
    }

    @Transactional
    public void delete(Long id) {
        if (!tagRepository.existsById(id)) {
            throw new BusinessException(404, "标签不存在");
        }
        tagRepository.deleteById(id);
    }

    private TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        dto.setCreatedAt(tag.getCreatedAt());
        return dto;
    }
}
