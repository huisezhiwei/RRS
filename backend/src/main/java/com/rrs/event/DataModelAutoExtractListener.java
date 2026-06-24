package com.rrs.event;

import com.rrs.dto.ExtractionRequest;
import com.rrs.entity.DataModel;
import com.rrs.entity.DataModelStatus;
import com.rrs.entity.TriggerType;
import com.rrs.repository.DataModelRepository;
import com.rrs.service.ExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataModelAutoExtractListener {

    private final DataModelRepository dataModelRepository;
    private final ExtractionService extractionService;

    @Async
    @EventListener
    public void onMaterialUploaded(MaterialUploadedEvent event) {
        log.info("Material uploaded event received: libraryId={}, materialId={}",
                event.getLibraryId(), event.getMaterialId());

        List<DataModel> models = dataModelRepository
                .findByLibraryIdAndStatus(event.getLibraryId(), DataModelStatus.READY);

        if (models.isEmpty()) {
            log.debug("No READY data models bound to library {}", event.getLibraryId());
            return;
        }

        for (DataModel model : models) {
            try {
                log.info("Auto-extracting for model: {}", model.getCode());
                ExtractionRequest request = new ExtractionRequest();
                request.setScopeType("INCREMENTAL");
                request.setFileIds(List.of(event.getMaterialId()));
                extractionService.extract(model.getId(), request, TriggerType.AUTO);
            } catch (Exception e) {
                log.error("Auto extraction failed for model {}: {}", model.getId(), e.getMessage(), e);
            }
        }
    }
}
