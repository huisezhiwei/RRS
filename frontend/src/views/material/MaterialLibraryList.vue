<template>
  <div class="library-list-page">
    <div class="page-header">
      <h2>素材库管理</h2>
      <div class="header-actions">
        <el-select v-model="filterType" placeholder="类型筛选" clearable style="width: 140px" @change="fetchLibraries">
          <el-option label="全部" value="" />
          <el-option label="Excel" value="EXCEL" />
          <el-option label="图片" value="IMAGE" />
        </el-select>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索素材库"
          clearable
          style="width: 200px"
          @keyup.enter="fetchLibraries"
          @clear="fetchLibraries"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          创建素材库
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col
        v-for="library in libraries"
        :key="library.id"
        :xs="24"
        :sm="12"
        :md="8"
        :lg="6"
      >
        <el-card
          class="library-card"
          shadow="hover"
          @click="goToDetail(library.id!)"
        >
          <template #header>
            <div class="card-header">
              <el-icon :size="24" :color="library.libraryType === 'EXCEL' ? '#67c23a' : '#409eff'">
                <component :is="library.libraryType === 'EXCEL' ? 'Document' : 'Picture'" />
              </el-icon>
              <span class="library-name">{{ library.name }}</span>
            </div>
          </template>
          <div class="card-content">
            <div class="info-row">
              <span class="label">编码:</span>
              <span class="value">{{ library.code }}</span>
            </div>
            <div class="info-row">
              <span class="label">类型:</span>
              <el-tag :type="library.libraryType === 'EXCEL' ? 'success' : 'primary'" size="small">
                {{ library.libraryType === 'EXCEL' ? 'Excel' : '图片' }}
              </el-tag>
            </div>
            <div class="info-row">
              <span class="label">素材数:</span>
              <span class="value">{{ library.materialCount || 0 }}</span>
            </div>
            <div class="info-row">
              <span class="label">维护人:</span>
              <span class="value">{{ library.maintainer || '-' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && libraries.length === 0" description="暂无素材库" />

    <CreateLibraryDialog
      v-model:visible="showCreateDialog"
      @created="handleCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLibraries, type MaterialLibrary } from '@/api/materialLibrary'
import CreateLibraryDialog from './components/CreateLibraryDialog.vue'

const router = useRouter()
const libraries = ref<MaterialLibrary[]>([])
const loading = ref(false)
const filterType = ref('')
const searchKeyword = ref('')
const showCreateDialog = ref(false)

const fetchLibraries = async () => {
  loading.value = true
  try {
    const params: { type?: string; keyword?: string } = {}
    if (filterType.value) params.type = filterType.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    libraries.value = await getLibraries(params)
  } catch (error: any) {
    ElMessage.error(error.message || '获取素材库列表失败')
  } finally {
    loading.value = false
  }
}

const goToDetail = (id: number) => {
  router.push(`/material/libraries/${id}`)
}

const handleCreated = () => {
  showCreateDialog.value = false
  fetchLibraries()
}

onMounted(() => {
  fetchLibraries()
})
</script>

<style scoped>
.library-list-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.library-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: transform 0.2s;
}

.library-card:hover {
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.library-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-row .label {
  color: #909399;
  font-size: 13px;
  min-width: 50px;
}

.info-row .value {
  color: #606266;
  font-size: 13px;
}
</style>
