<template>
  <div class="ddl-editor">
    <div class="ddl-toolbar">
      <span class="ddl-title">DDL 语句</span>
      <el-tag v-if="parsedFields.length > 0" type="info" size="small">
        {{ parsedFields.length }} 个字段
      </el-tag>
    </div>
    <el-input
      v-model="localDdl"
      type="textarea"
      :rows="12"
      placeholder="CREATE TABLE dm_example (&#10;  name TEXT, -- 姓名&#10;  amount REAL, -- 金额&#10;  record_date TEXT -- 日期&#10;)"
      style="font-family: 'Consolas', 'Monaco', monospace"
      @input="handleDdlChange"
    />
    <div class="ddl-fields" v-if="parsedFields.length > 0">
      <div class="fields-header">
        <span>解析字段：</span>
      </div>
      <el-tag
        v-for="field in parsedFields"
        :key="field.name"
        :type="field.name === primaryKey ? 'danger' : 'default'"
        class="field-tag"
        @click="$emit('selectPrimaryKey', field.name)"
      >
        {{ field.name }}
        <span class="field-type">{{ field.type }}</span>
        <span v-if="field.comment" class="field-comment">{{ field.comment }}</span>
      </el-tag>
    </div>
    <div class="pk-selector" v-if="parsedFields.length > 0">
      <span>主键字段：</span>
      <el-select v-model="localPrimaryKey" placeholder="选择主键（可选）" clearable @change="handlePkChange" style="width: 200px">
        <el-option v-for="field in parsedFields" :key="field.name" :label="field.name" :value="field.name" />
      </el-select>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'

const props = defineProps<{
  ddl?: string
  primaryKey?: string
}>()

const emit = defineEmits<{
  'update:ddl': [value: string]
  'update:primaryKey': [value: string]
  'selectPrimaryKey': [name: string]
}>()

const localDdl = ref(props.ddl || '')
const localPrimaryKey = ref(props.primaryKey || '')

watch(() => props.ddl, (val) => { if (val !== localDdl.value) localDdl.value = val || '' })
watch(() => props.primaryKey, (val) => { if (val !== localPrimaryKey.value) localPrimaryKey.value = val || '' })

const handleDdlChange = () => {
  emit('update:ddl', localDdl.value)
}

const handlePkChange = (val: string) => {
  emit('update:primaryKey', val)
}

interface ParsedField {
  name: string
  type: string
  comment?: string
}

const parsedFields = computed<ParsedField[]>(() => {
  if (!localDdl.value) return []
  const fields: ParsedField[] = []
  // Extract content between ( and )
  const match = localDdl.value.match(/\(([\s\S]*)\)/)
  if (!match) return fields

  const lines = match[1].split(',')
  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.toUpperCase().includes('PRIMARY KEY') && trimmed.toUpperCase().startsWith('PRIMARY')) continue

    // Parse: field_name TYPE -- comment
    const commentMatch = trimmed.match(/--\s*(.+)$/)
    const comment = commentMatch ? commentMatch[1].trim() : undefined
    const withoutComment = trimmed.replace(/--.*$/, '').trim()

    const parts = withoutComment.split(/\s+/)
    if (parts.length >= 2) {
      const name = parts[0].replace(/[`"[\]]/g, '')
      const type = parts[1].replace(/[,(]/g, '')
      if (name && type && /^[a-zA-Z_]/.test(name)) {
        fields.push({ name, type, comment })
      }
    }
  }
  return fields
})
</script>

<style scoped>
.ddl-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ddl-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ddl-title {
  font-weight: 600;
  font-size: 14px;
}

.ddl-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.fields-header {
  font-size: 13px;
  color: #909399;
  margin-right: 4px;
}

.field-tag {
  cursor: pointer;
}

.field-type {
  margin-left: 4px;
  font-size: 11px;
  color: #909399;
}

.field-comment {
  margin-left: 4px;
  font-size: 11px;
  color: #67c23a;
}

.pk-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
</style>
