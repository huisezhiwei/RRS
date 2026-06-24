package com.rrs.service;

import com.rrs.dto.DataModelScheduleDTO;
import com.rrs.dto.ExtractionRequest;
import com.rrs.entity.*;
import com.rrs.repository.DataModelScheduleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataModelScheduleService {

    private final DataModelScheduleRepository scheduleRepository;
    private final DataModelService dataModelService;
    private final ExtractionService extractionService;
    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Loading enabled data model schedules...");
        List<DataModelSchedule> schedules = scheduleRepository.findByEnabledTrue();
        for (DataModelSchedule schedule : schedules) {
            try {
                registerTask(schedule);
                log.info("Registered schedule for model {}: cron={}", schedule.getModelId(), schedule.getCronExpression());
            } catch (Exception e) {
                log.error("Failed to register schedule for model {}: {}", schedule.getModelId(), e.getMessage());
            }
        }
        log.info("Loaded {} schedules", scheduledTasks.size());
    }

    public DataModelScheduleDTO getSchedule(Long modelId) {
        return scheduleRepository.findByModelId(modelId)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    public DataModelScheduleDTO saveSchedule(Long modelId, DataModelScheduleDTO dto) {
        dataModelService.getEntity(modelId); // validate exists

        DataModelSchedule schedule = scheduleRepository.findByModelId(modelId)
                .orElse(new DataModelSchedule());

        schedule.setModelId(modelId);
        schedule.setCronExpression(dto.getCronExpression());
        schedule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : false);
        schedule.setScopeType(dto.getScopeType() != null
                ? ScopeType.valueOf(dto.getScopeType())
                : ScopeType.INCREMENTAL);

        schedule = scheduleRepository.save(schedule);

        // Re-register or cancel task
        cancelTask(modelId);
        if (schedule.getEnabled()) {
            registerTask(schedule);
        }

        log.info("Schedule saved for model {}: cron={}, enabled={}", modelId, schedule.getCronExpression(), schedule.getEnabled());
        return toDTO(schedule);
    }

    @Transactional
    public void removeSchedule(Long modelId) {
        scheduleRepository.findByModelId(modelId).ifPresent(schedule -> {
            cancelTask(modelId);
            scheduleRepository.delete(schedule);
            log.info("Schedule removed for model {}", modelId);
        });
    }

    private void registerTask(DataModelSchedule schedule) {
        try {
            CronTrigger trigger = new CronTrigger(schedule.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeScheduledExtraction(schedule),
                    trigger
            );
            scheduledTasks.put(schedule.getModelId(), future);
        } catch (Exception e) {
            log.error("Invalid cron expression '{}' for model {}: {}",
                    schedule.getCronExpression(), schedule.getModelId(), e.getMessage());
        }
    }

    private void cancelTask(Long modelId) {
        ScheduledFuture<?> future = scheduledTasks.remove(modelId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled scheduled task for model {}", modelId);
        }
    }

    private void executeScheduledExtraction(DataModelSchedule schedule) {
        try {
            DataModel model = dataModelService.getEntity(schedule.getModelId());
            if (model.getStatus() != DataModelStatus.READY) {
                log.debug("Model {} is not READY, skipping scheduled extraction", model.getCode());
                return;
            }

            log.info("Executing scheduled extraction for model {}: scope={}",
                    model.getCode(), schedule.getScopeType());

            ExtractionRequest request = new ExtractionRequest();
            request.setScopeType(schedule.getScopeType().name());
            extractionService.extract(model.getId(), request, TriggerType.SCHEDULED);
        } catch (Exception e) {
            log.error("Scheduled extraction failed for model {}: {}", schedule.getModelId(), e.getMessage(), e);
        }
    }

    private DataModelScheduleDTO toDTO(DataModelSchedule entity) {
        DataModelScheduleDTO dto = new DataModelScheduleDTO();
        dto.setId(entity.getId());
        dto.setModelId(entity.getModelId());
        dto.setCronExpression(entity.getCronExpression());
        dto.setEnabled(entity.getEnabled());
        dto.setScopeType(entity.getScopeType().name());
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return dto;
    }
}
