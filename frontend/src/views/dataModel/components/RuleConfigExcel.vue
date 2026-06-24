<template>
  <div class="rule-config-excel">
    <div class="config-section">
      <el-form :model="config" label-width="120px" size="small">
        <el-form-item label="Sheet 索引">
          <el-input-number v-model="config.sheetIndex" :min="0" />
        </el-form-item>
        <el-form-item label="表头行索引">
          <el-input-number v-model="config.headerRowIndex" :min="0" />
        </el-form-item>
        <el-form-item label="数据起始行">
          <el-input-number v-model="config.dataStartRowIndex" :min="1" />
        </el-form-item>
      </el-form>
    </div>

    <div class="mappings-section">
      <div class="mappings-header">
        <span class="mappings-title">字段映射</span>
        <el-button type="primary" size="small" @click="addMapping">
          <el-icon><Plus /></el-icon>
          添加映射
        </el-button>
      </div>
      <el-table :data="config.mappings" border size="small" style="width: 100%">
        <el-table-column label="源列名（Excel表头）" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.sourceColumn" placeholder="Excel 列名" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="目标字段（数据库列）" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.targetField" placeholder="数据库字段名" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="格式化" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.formatterType" placeholder="无" clearable size="small" style="width: 100%">
              <el-option label="无" value="" />
              <el-option label="数字格式化" value="NUMBER_TO_STRING" />
              <el-option label="日期格式化" value="DATE_FORMAT" />
              <el-option label="去除空白" value="TRIM" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="格式模板" min-width="120">
          <template #default="{ row }">
            <el-input
              v-if="row.formatterType"
              v-model="row.formatterPattern"
              placeholder="#.00"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button type="danger" size="small" text @click="removeMapping($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface MappingRow {
  sourceColumn: string
  targetField: string
  formatterType: string
  formatterPattern: string
}

const props = defineProps<{
  ruleContent?: string
}>()

const emit = defineEmits<{
  'update:ruleContent': [value: string]
}>()

const config = ref({
  sheetIndex: 0,
  headerRowIndex: 0,
  dataStartRowIndex: 1,
  mappings: [] as MappingRow[],
})

// Parse incoming ruleContent
watch(() => props.ruleContent, (val) => {
  if (val) {
    try {
      const parsed = JSON.parse(val)
      config.value.sheetIndex = parsed.sheetIndex ?? 0
      config.value.headerRowIndex = parsed.headerRowIndex ?? 0
      config.value.dataStartRowIndex = parsed.dataStartRowIndex ?? 1
      config.value.mappings = (parsed.mappings || []).map((m: any) => ({
        sourceColumn: m.sourceColumn || '',
        targetField: m.targetField || '',
        formatterType: m.formatter?.type || '',
        formatterPattern: m.formatter?.pattern || '',
      }))
    } catch { /* ignore */ }
  }
}, { immediate: true })

const addMapping = () => {
  config.value.mappings.push({ sourceColumn: '', targetField: '', formatterType: '', formatterPattern: '' })
}

const removeMapping = (index: number) => {
  config.value.mappings.splice(index, 1)
}

// Build JSON output
const buildJson = () => {
  return JSON.stringify({
    sheetIndex: config.value.sheetIndex,
    headerRowIndex: config.value.headerRowIndex,
    dataStartRowIndex: config.value.dataStartRowIndex,
    mappings: config.value.mappings.map(m => ({
      sourceColumn: m.sourceColumn,
      targetField: m.targetField,
      formatter: m.formatterType ? { type: m.formatterType, pattern: m.formatterPattern } : null,
    })),
  })
}

// Auto-emit on changes
watch(config, () => {
  emit('update:ruleContent', buildJson())
}, { deep: true })

defineExpose({ buildJson })
</script>

<style scoped>
.rule-config-excel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mappings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.mappings-title {
  font-weight: 600;
  font-size: 14px;
}
</style>
