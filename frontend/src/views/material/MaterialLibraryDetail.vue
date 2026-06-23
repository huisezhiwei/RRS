<template>
  <div class="library-detail-page" v-loading="loadingLibrary">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push('/material/libraries')" text>
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </el-button>
        <h2 v-if="library">{{ library.name }}</h2>
        <el-tag v-if="library" :type="library.libraryType === 'EXCEL' ? 'success' : 'primary'" size="small">
          {{ library.libraryType === 'EXCEL' ? 'Excel' : '图片' }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button type="danger" @click="handleDeleteLibrary" v-if="library">
          <el-icon><Delete /></el-icon>
          删除素材库
        </el-button>
      </div>
    </div>

    <el-descriptions v-if="library" :column="3" border class="library-info">
      <el-descriptions-item label="英文编码">{{ library.code }}</el-descriptions-item>
      <el-descriptions-item label="维护人">{{ library.maintainer || '-' }}</el-descriptions-item>
      <el-descriptions-item label="素材数量">{{ library.materialCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(library.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="最后上传">{{ formatTime(library.lastUploadedAt) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="描述" :span="3">{{ library.description || '-' }}</el-descriptions-item>
    </el-descriptions>

    <div class="toolbar" v-if="library">
      <el-button type="primary" @click="showUploadDialog = true">
        <el-icon><Upload /></el-icon>
        上传素材
      </el-button>
      <el-select v-model="filterTag" placeholder="标签筛选" clearable style="width: 160px" @change="fetchMaterials">
        <el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.name" />
      </el-select>
    </div>

    <el-table :data="materials" v-loading="loadingMaterials" stripe style="width: 100%">
      <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="uploadedAt" label="上传时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.uploadedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="fileSize" label="大小" width="100">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="标签" width="250">
        <template #default="{ row }">
          <div class="tag-cell">
            <el-tag
              v-for="tag in row.tags"
              :key="tag.id"
              :color="tag.color"
              size="small"
              style="margin-right: 4px; margin-bottom: 2px"
            >
              {{ tag.name }}
            </el-tag>
            <el-button
              type="primary"
              size="small"
              text
              @click="openTagManager(row)"
            >
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="handleDownload(row)">
            <el-icon><Download /></el-icon>
          </el-button>
          <el-button type="danger" size="small" text @click="handleDelete(row)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchMaterials"
      />
    </div>

    <UploadMaterialDialog
      v-model:visible="showUploadDialog"
      :library-id="libraryId"
      :library-type="library?.libraryType"
      @uploaded="handleUploaded"
    />

    <TagManager
      v-model:visible="showTagManager"
      :material="selectedMaterial"
      :all-tags="allTags"
      @updated="handleTagsUpdated"
      @tags-changed="handleTagsChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLibraryById, deleteLibrary, type MaterialLibrary } from '@/api/materialLibrary'
import {
  getMaterials,
  downloadMaterial,
  deleteMaterial,
  getTags,
  type Material,
  type Tag,
} from '@/api/material'
import UploadMaterialDialog from './components/UploadMaterialDialog.vue'
import TagManager from './components/TagManager.vue'

const route = useRoute()
const router = useRouter()
const libraryId = computed(() => Number(route.params.id))

const library = ref<MaterialLibrary | null>(null)
const materials = ref<Material[]>([])
const allTags = ref<Tag[]>([])
const loadingLibrary = ref(false)
const loadingMaterials = ref(false)
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
const filterTag = ref('')
const showUploadDialog = ref(false)
const showTagManager = ref(false)
const selectedMaterial = ref<Material | null>(null)

const fetchLibrary = async () => {
  loadingLibrary.value = true
  try {
    library.value = await getLibraryById(libraryId.value)
  } catch (error: any) {
    ElMessage.error(error.message || '获取素材库信息失败')
  } finally {
    loadingLibrary.value = false
  }
}

const fetchMaterials = async () => {
  loadingMaterials.value = true
  try {
    const params: { page: number; size: number; tag?: string } = {
      page: currentPage.value - 1,
      size: pageSize,
    }
    if (filterTag.value) params.tag = filterTag.value
    const result = await getMaterials(libraryId.value, params)
    materials.value = result.content
    total.value = result.totalElements
  } catch (error: any) {
    ElMessage.error(error.message || '获取素材列表失败')
  } finally {
    loadingMaterials.value = false
  }
}

const fetchTags = async () => {
  try {
    allTags.value = await getTags()
  } catch (error: any) {
    console.error('获取标签失败', error)
  }
}

const handleDeleteLibrary = async () => {
  try {
    await ElMessageBox.confirm('确定要删除该素材库吗？所有素材和文件都将被删除。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLibrary(libraryId.value)
    ElMessage.success('素材库已删除')
    router.push('/material/libraries')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleDownload = async (material: Material) => {
  try {
    await downloadMaterial(material.id!, material.fileName!)
  } catch (error: any) {
    ElMessage.error('下载失败')
  }
}

const handleDelete = async (material: Material) => {
  try {
    await ElMessageBox.confirm('确定要删除该素材吗？', '确认删除', { type: 'warning' })
    await deleteMaterial(material.id!)
    ElMessage.success('素材已删除')
    fetchMaterials()
    fetchLibrary()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const openTagManager = (material: Material) => {
  selectedMaterial.value = material
  showTagManager.value = true
}

const handleUploaded = () => {
  showUploadDialog.value = false
  fetchMaterials()
  fetchLibrary()
}

const handleTagsUpdated = () => {
  showTagManager.value = false
  fetchMaterials()
}

const handleTagsChanged = () => {
  fetchTags()
}

const formatTime = (time?: string) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

const formatSize = (bytes?: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  fetchLibrary()
  fetchMaterials()
  fetchTags()
})
</script>

<style scoped>
.library-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.library-info {
  margin-bottom: 24px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.tag-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
