<template>
  <div class="rule-config-image">
    <el-form :model="config" label-width="120px">
      <el-form-item label="AI Agent">
        <el-select v-model="config.agentId" placeholder="选择 Agent" style="width: 100%">
          <el-option v-for="a in agents" :key="a.id" :label="a.name" :value="a.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="系统提示词">
        <el-input
          v-model="config.systemPrompt"
          type="textarea"
          :rows="4"
          placeholder="你是数据提取专家，请从OCR文本中提取结构化数据，以JSON数组格式返回..."
        />
      </el-form-item>
      <el-form-item label="用户提示词模板">
        <el-input
          v-model="config.userPromptTemplate"
          type="textarea"
          :rows="3"
          placeholder="请从以下OCR文本中提取关键字段，返回JSON数组：&#10;{text}"
        />
        <div class="prompt-hint">使用 <code>{text}</code> 作为 OCR 文本的占位符</div>
      </el-form-item>
      <el-form-item label="输出字段">
        <div class="output-fields">
          <el-tag
            v-for="(field, index) in config.outputFields"
            :key="index"
            closable
            @close="removeField(index)"
            class="field-tag"
          >
            {{ field }}
          </el-tag>
          <el-input
            v-if="showFieldInput"
            v-model="newField"
            size="small"
            style="width: 120px"
            placeholder="字段名"
            @keyup.enter="addField"
            @blur="addField"
          />
          <el-button v-else size="small" @click="showFieldInput = true">
            <el-icon><Plus /></el-icon>
            添加字段
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AiAgent } from '@/api/dataModel'

const props = defineProps<{
  ruleContent?: string
  agents?: AiAgent[]
}>()

const emit = defineEmits<{
  'update:ruleContent': [value: string]
}>()

const config = ref({
  agentId: null as number | null,
  systemPrompt: '你是数据提取专家。请从OCR文本中提取结构化数据，以JSON格式返回。只返回JSON，不要其他文字。',
  userPromptTemplate: '请从以下OCR文本中提取关键字段，返回JSON数组：\n{text}',
  outputFields: [] as string[],
})

const showFieldInput = ref(false)
const newField = ref('')

// Parse incoming ruleContent
watch(() => props.ruleContent, (val) => {
  if (val) {
    try {
      const parsed = JSON.parse(val)
      config.value.agentId = parsed.agentId || null
      config.value.systemPrompt = parsed.systemPrompt || config.value.systemPrompt
      config.value.userPromptTemplate = parsed.userPromptTemplate || config.value.userPromptTemplate
      config.value.outputFields = parsed.outputFields || []
    } catch { /* ignore */ }
  }
}, { immediate: true })

const addField = () => {
  if (newField.value.trim()) {
    config.value.outputFields.push(newField.value.trim())
  }
  newField.value = ''
  showFieldInput.value = false
}

const removeField = (index: number) => {
  config.value.outputFields.splice(index, 1)
}

const buildJson = () => {
  return JSON.stringify({
    agentId: config.value.agentId,
    systemPrompt: config.value.systemPrompt,
    userPromptTemplate: config.value.userPromptTemplate,
    outputFields: config.value.outputFields,
    ocrMode: 'LLM',
  })
}

watch(config, () => {
  emit('update:ruleContent', buildJson())
}, { deep: true })

defineExpose({ buildJson })
</script>

<style scoped>
.rule-config-image {
  max-width: 700px;
}

.prompt-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.prompt-hint code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  color: #e6a23c;
}

.output-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.field-tag {
  margin: 0;
}
</style>
