<template>
  <el-dialog v-model="visible" title="编辑记录" width="600px" @close="handleClose">
    <el-form :model="editData" label-width="120px" v-if="editData">
      <el-form-item v-for="col in columns" :key="col" :label="col">
        <el-input v-model="editData[col]" :placeholder="col" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  row?: Record<string, any>
  columns?: string[]
  modelId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'saved': []
}>()

const visible = ref(props.modelValue)
const editData = ref<Record<string, any>>({})
const saving = ref(false)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.row) {
    editData.value = { ...props.row }
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const handleClose = () => {
  visible.value = false
  editData.value = {}
}

const handleSave = async () => {
  if (!props.row?._id) return
  saving.value = true
  try {
    const { updateRow } = await import('@/api/dataModel')
    await updateRow(props.modelId, props.row._id, editData.value)
    emit('saved')
    handleClose()
  } catch (error: any) {
    const { ElMessage } = await import('element-plus')
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>
