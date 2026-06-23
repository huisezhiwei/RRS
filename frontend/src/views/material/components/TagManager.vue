<template>
  <el-dialog
    :model-value="visible"
    title="管理标签"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
    @open="initTags"
  >
    <div class="current-tags">
      <el-tag
        v-for="tag in currentTags"
        :key="tag.id"
        closable
        :color="tag.color"
        @close="removeTag(tag)"
        style="margin-right: 8px; margin-bottom: 8px"
      >
        {{ tag.name }}
      </el-tag>
      <span v-if="currentTags.length === 0" class="no-tags">暂无标签</span>
    </div>

    <el-divider />

    <div class="add-tag-section">
      <el-select
        v-model="selectedTagIds"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入新标签"
        style="width: 100%"
      >
        <el-option
          v-for="tag in availableTags"
          :key="tag.id"
          :label="tag.name"
          :value="tag.id"
        />
      </el-select>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="saveTags" :loading="saving">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { setMaterialTags, createTag, getTags, type Material, type Tag } from '@/api/material'

const props = defineProps<{
  visible: boolean
  material: Material | null
  allTags: Tag[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  updated: []
  'tags-changed': []
}>()

const currentTags = ref<Tag[]>([])
const selectedTagIds = ref<(number | string)[]>([])
const saving = ref(false)

const availableTags = computed(() => {
  return props.allTags.filter(
    (tag) => !currentTags.value.find((t) => t.id === tag.id)
  )
})

const initTags = () => {
  if (props.material?.tags) {
    currentTags.value = [...props.material.tags]
    selectedTagIds.value = currentTags.value.map((t) => t.id!)
  } else {
    currentTags.value = []
    selectedTagIds.value = []
  }
}

const removeTag = (tag: Tag) => {
  currentTags.value = currentTags.value.filter((t) => t.id !== tag.id)
  selectedTagIds.value = selectedTagIds.value.filter((id) => id !== tag.id)
}

const saveTags = async () => {
  if (!props.material?.id) return

  saving.value = true
  try {
    const tagIds: number[] = []
    let hasNewTags = false

    for (const id of selectedTagIds.value) {
      if (typeof id === 'number') {
        tagIds.push(id)
      } else if (typeof id === 'string' && id.trim()) {
        // It's a string, create new tag
        try {
          const newTag = await createTag({ name: id.trim() })
          tagIds.push(newTag.id!)
          hasNewTags = true
        } catch (e: any) {
          // 标签可能已存在（并发创建时），尝试获取已有标签
          const existingTags = await getTags()
          const found = existingTags.find((t) => t.name === id.trim())
          if (found && found.id) {
            tagIds.push(found.id)
          } else {
            throw e
          }
        }
      }
    }

    await setMaterialTags(props.material.id, tagIds)
    ElMessage.success('标签已更新')

    // 如果有新创建的标签，通知父组件刷新标签列表
    if (hasNewTags) {
      emit('tags-changed')
    }
    emit('updated')
  } catch (error: any) {
    ElMessage.error(error.message || '更新标签失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.current-tags {
  min-height: 40px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.no-tags {
  color: #909399;
  font-size: 14px;
}

.add-tag-section {
  margin-top: 16px;
}
</style>
