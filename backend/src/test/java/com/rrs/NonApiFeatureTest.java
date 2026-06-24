package com.rrs;

import com.rrs.config.DataInitializer;
import com.rrs.entity.*;
import com.rrs.event.DataModelAutoExtractListener;
import com.rrs.event.MaterialUploadedEvent;
import com.rrs.repository.*;
import com.rrs.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NonApiFeatureTest {

    @Autowired private AiAgentService aiAgentService;
    @Autowired private AiAgentRepository aiAgentRepository;
    @Autowired private DataModelScheduleService scheduleService;
    @Autowired private DataModelScheduleRepository scheduleRepository;
    @Autowired private DataModelService dataModelService;
    @Autowired private DataModelRepository dataModelRepository;
    @Autowired private ExtractionRuleRepository extractionRuleRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;

    // ==================== DataInitializer Tests ====================

    @Test
    @Order(1)
    @DisplayName("DataInitializer: Built-in agents are initialized on startup")
    void testBuiltInAgentsInitialized() {
        Optional<AiAgent> chatAgent = aiAgentRepository.findByCode("chat_assistant");
        assertTrue(chatAgent.isPresent(), "chat_assistant should exist after startup");
        assertEquals(AgentType.CHAT_ASSISTANT, chatAgent.get().getAgentType());
        assertEquals("Chat Assistant", chatAgent.get().getName());
        assertNotNull(chatAgent.get().getCreatedAt());

        Optional<AiAgent> ocrAgent = aiAgentRepository.findByCode("ocr_assistant");
        assertTrue(ocrAgent.isPresent(), "ocr_assistant should exist after startup");
        assertEquals(AgentType.OCR_ASSISTANT, ocrAgent.get().getAgentType());
        assertEquals("OCR 图片解析助手", ocrAgent.get().getName());
        assertNotNull(ocrAgent.get().getSystemPrompt());
    }

    @Test
    @Order(2)
    @DisplayName("DataInitializer: Re-running initBuiltInAgents does not create duplicates")
    void testInitBuiltInAgentsIdempotent() {
        long countBefore = aiAgentRepository.count();
        aiAgentService.initBuiltInAgents();
        long countAfter = aiAgentRepository.count();
        assertEquals(countBefore, countAfter, "Agent count should not change on re-init");
    }

    // ==================== DataModelScheduleService Tests ====================

    @Test
    @Order(10)
    @DisplayName("ScheduleService: Save and retrieve schedule")
    void testSaveAndGetSchedule() {
        // Create a data model first
        DataModel model = createTestModel("sched_test_model");

        com.rrs.dto.DataModelScheduleDTO dto = new com.rrs.dto.DataModelScheduleDTO();
        dto.setCronExpression("0 0 3 * * ?");
        dto.setEnabled(true);
        dto.setScopeType("FULL");

        com.rrs.dto.DataModelScheduleDTO saved = scheduleService.saveSchedule(model.getId(), dto);
        assertNotNull(saved.getId());
        assertEquals("0 0 3 * * ?", saved.getCronExpression());
        assertTrue(saved.getEnabled());
        assertEquals("FULL", saved.getScopeType());

        // Retrieve
        com.rrs.dto.DataModelScheduleDTO retrieved = scheduleService.getSchedule(model.getId());
        assertNotNull(retrieved);
        assertEquals(saved.getId(), retrieved.getId());

        // Cleanup
        cleanupTestModel(model.getId());
    }

    @Test
    @Order(11)
    @DisplayName("ScheduleService: Cancel and remove schedule")
    void testRemoveSchedule() {
        DataModel model = createTestModel("sched_remove_test");

        com.rrs.dto.DataModelScheduleDTO dto = new com.rrs.dto.DataModelScheduleDTO();
        dto.setCronExpression("0 30 2 * * ?");
        dto.setEnabled(true);
        dto.setScopeType("INCREMENTAL");
        scheduleService.saveSchedule(model.getId(), dto);

        // Remove
        scheduleService.removeSchedule(model.getId());
        assertNull(scheduleService.getSchedule(model.getId()));

        cleanupTestModel(model.getId());
    }

    @Test
    @Order(12)
    @DisplayName("ScheduleService: Get schedule for model without schedule returns null")
    void testGetScheduleReturnsNullWhenNone() {
        DataModel model = createTestModel("sched_none_test");
        assertNull(scheduleService.getSchedule(model.getId()));
        cleanupTestModel(model.getId());
    }

    @Test
    @Order(13)
    @DisplayName("ScheduleService: Update existing schedule")
    void testUpdateSchedule() {
        DataModel model = createTestModel("sched_update_test");

        com.rrs.dto.DataModelScheduleDTO dto1 = new com.rrs.dto.DataModelScheduleDTO();
        dto1.setCronExpression("0 0 1 * * ?");
        dto1.setEnabled(true);
        dto1.setScopeType("FULL");
        com.rrs.dto.DataModelScheduleDTO saved1 = scheduleService.saveSchedule(model.getId(), dto1);

        com.rrs.dto.DataModelScheduleDTO dto2 = new com.rrs.dto.DataModelScheduleDTO();
        dto2.setCronExpression("0 0 5 * * ?");
        dto2.setEnabled(false);
        dto2.setScopeType("INCREMENTAL");
        com.rrs.dto.DataModelScheduleDTO saved2 = scheduleService.saveSchedule(model.getId(), dto2);

        // Should update the same record (same ID)
        assertEquals(saved1.getId(), saved2.getId());
        assertEquals("0 0 5 * * ?", saved2.getCronExpression());
        assertFalse(saved2.getEnabled());

        cleanupTestModel(model.getId());
    }

    @Test
    @Order(14)
    @DisplayName("ScheduleService: Invalid cron expression is handled gracefully")
    void testInvalidCronExpression() {
        DataModel model = createTestModel("sched_bad_cron");

        com.rrs.dto.DataModelScheduleDTO dto = new com.rrs.dto.DataModelScheduleDTO();
        dto.setCronExpression("invalid_cron");
        dto.setEnabled(true);
        dto.setScopeType("FULL");

        // Should not throw - the task registration fails silently
        assertDoesNotThrow(() -> scheduleService.saveSchedule(model.getId(), dto));

        cleanupTestModel(model.getId());
    }

    // ==================== DataModelAutoExtractListener Tests ====================

    @Test
    @Order(20)
    @DisplayName("AutoExtractListener: Event is published and received without error")
    void testMaterialUploadedEventPublish() {
        // Create library and model
        DataModel model = createTestModel("auto_extract_test");

        // Publish event (should not throw even if model is UNINITIALIZED)
        assertDoesNotThrow(() -> {
            MaterialUploadedEvent event = new MaterialUploadedEvent(this, 1L, 1L);
            eventPublisher.publishEvent(event);
        });

        cleanupTestModel(model.getId());
    }

    @Test
    @Order(21)
    @DisplayName("AutoExtractListener: Event with no matching models does not error")
    void testMaterialUploadedEventNoModels() {
        assertDoesNotThrow(() -> {
            MaterialUploadedEvent event = new MaterialUploadedEvent(this, 99999L, 99999L);
            eventPublisher.publishEvent(event);
        });
    }

    // ==================== ExtractionRule Versioning Tests ====================

    @Test
    @Order(30)
    @DisplayName("ExtractionRule: Save rule creates version 1")
    void testSaveFirstRule() {
        DataModel model = createTestModel("rule_version_test");

        com.rrs.dto.ExtractionRuleSaveRequest req = new com.rrs.dto.ExtractionRuleSaveRequest();
        req.setRuleType("EXCEL_MAPPING");
        req.setRuleContent("{\"test\": true}");

        com.rrs.dto.ExtractionRuleDTO rule = dataModelService.saveRule(model.getId(), req);
        assertEquals(1, rule.getVersion());
        assertTrue(rule.getActive());
        assertEquals("EXCEL_MAPPING", rule.getRuleType());

        cleanupTestModel(model.getId());
    }

    @Test
    @Order(31)
    @DisplayName("ExtractionRule: Saving new rule deactivates old and increments version")
    void testRuleVersioning() {
        DataModel model = createTestModel("rule_v_test2");

        // Save first rule
        com.rrs.dto.ExtractionRuleSaveRequest req1 = new com.rrs.dto.ExtractionRuleSaveRequest();
        req1.setRuleType("EXCEL_MAPPING");
        req1.setRuleContent("{\"version\": 1}");
        dataModelService.saveRule(model.getId(), req1);

        // Save second rule
        com.rrs.dto.ExtractionRuleSaveRequest req2 = new com.rrs.dto.ExtractionRuleSaveRequest();
        req2.setRuleType("EXCEL_MAPPING");
        req2.setRuleContent("{\"version\": 2}");
        com.rrs.dto.ExtractionRuleDTO rule2 = dataModelService.saveRule(model.getId(), req2);

        assertEquals(2, rule2.getVersion());
        assertTrue(rule2.getActive());

        // Active rule should be v2
        com.rrs.dto.ExtractionRuleDTO active = dataModelService.getActiveRule(model.getId());
        assertNotNull(active);
        assertEquals(2, active.getVersion());

        cleanupTestModel(model.getId());
    }

    @Test
    @Order(32)
    @DisplayName("ExtractionRule: getActiveRule returns null when no rules")
    void testGetActiveRuleEmpty() {
        DataModel model = createTestModel("rule_empty_test");
        assertNull(dataModelService.getActiveRule(model.getId()));
        cleanupTestModel(model.getId());
    }

    // ==================== Helpers ====================

    private DataModel createTestModel(String code) {
        com.rrs.dto.DataModelCreateDTO dto = new com.rrs.dto.DataModelCreateDTO();
        dto.setCode(code);
        dto.setName("Test: " + code);
        return dataModelRepository.save(toEntity(dto));
    }

    private DataModel toEntity(com.rrs.dto.DataModelCreateDTO dto) {
        DataModel m = new DataModel();
        m.setCode(dto.getCode());
        m.setName(dto.getName());
        m.setDescription(dto.getDescription());
        m.setTableName("dm_" + dto.getCode());
        m.setStatus(DataModelStatus.UNINITIALIZED);
        return m;
    }

    private void cleanupTestModel(Long modelId) {
        try {
            dataModelService.delete(modelId);
        } catch (Exception ignored) {}
    }
}
