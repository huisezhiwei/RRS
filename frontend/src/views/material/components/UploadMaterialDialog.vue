<template>
  <el-dialog
    :model-value="visible"
    title="上传素材"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-upload
      ref="uploadRef"
      drag
      :action="''"
      :http-request="handleUpload"
      :accept="acceptTypes"
      multiple
      :on-success="handleSuccess"
      :on-error="handleError"
      :file-list="fileList"
    >
      <el-icon class="el-icon--upload"><Upload /></el-icon>
      <div class="el-upload__text">
        将文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          {{ libraryType === 'EXCEL' ? '支持 .xls, .xlsx, .csv 格式' : '支持常见图片格式 (jpg, png, gif, bmp, webp)' }}
        </div>
      </template>
    </el-upload>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadMaterial } from '@/api/material'
import type { UploadFile, UploadRequestOptions } from 'element-plus'

const props = defineProps<{
  visible: boolean
  libraryId: number
  libraryType?: 'EXCEL' | 'IMAGE'
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  uploaded: []
}>()

const uploadRef = ref()
const fileList = ref<UploadFile[]>([])

const acceptTypes = computed(() => {
  return props.libraryType === 'EXCEL'
    ? '.xls,.xlsx,.csv'
    : 'image/*'
})

const handleUpload = async (options: UploadRequestOptions) => {
  try {
    await uploadMaterial(props.libraryId, options.file as File)
    ElMessage.success(`${options.file.name} 上传成功`)
    emit('uploaded')
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
    options.onError?.(error)
  }
}

const handleSuccess = () => {
  fileList.value = []
}

const handleError = () => {
  // Error already handled in handleUpload
}
</script>
