<template>
  <div class="data-model-list-page">
    <div class="page-header">
      <h2>数据模型</h2>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索模型"
          clearable
          style="width: 200px"
          @keyup.enter="fetchModels"
          @clear="fetchModels"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          新建模型
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col
        v-for="model in models"
        :key="model.id"
        :xs="24" :sm="12" :md="8" :lg="6"
      >
        <el-card class="model-card" shadow="hover" @click="goToDetail(model)">
          <template #header>
            <div class="card-header">
              <el-icon :size="24" color="#409eff"><Coin /></el-icon>
              <span class="model-name">{{ model.name }}</span>
              <el-tag :type="statusTagType(model.status)" size="small" class="status-tag">
                {{ statusLabel(model.status) }}
              </el-tag>
            </div>
          </template>
          <div class="card-content">
            <div class="info-row">
              <span class="label">编码:</span>
              <span class="value">{{ model.code }}</span>
            </div>
            <div class="info-row">
              <span class="label">素材库:</span>
              <span class="value">{{ model.libraryName || '未绑定' }}</span>
            </div>
            <div class="info-row">
              <span class="label">数据量:</span>
              <span class="value">{{ model.dataCount ?? 0 }} 条</span>
            </div>
            <div class="info-row">
              <span class="label">维护人:</span>
              <span class="value">{{ model.maintainer || '-' }}</span>
            </div>
          </div>
          <div class="card-footer">
            <el-button size="small" text type="danger" @click.stop="handleDelete(model)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && models.length === 0" description="暂无数据模型" />

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" title="新建数据模型" width="480px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="编码" required>
          <el-input v-model="createForm.code" placeholder="字母开头，仅字母/数字/下划线" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="模型名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="模型描述" />
        </el-form-item>
        <el-form-item label="维护人">
          <el-input v-model="createForm.maintainer" placeholder="维护人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDataModels, createDataModel, deleteDataModel, type DataModel } from '@/api/dataModel'

const router = useRouter()
const models = ref<DataModel[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = ref({ code: '', name: '', description: '', maintainer: '' })

const fetchModels = async () => {
  loading.value = true
  try {
    const params: { keyword?: string } = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value
    models.value = await getDataModels(params)
  } catch (error: any) {
    ElMessage.error(error.message || '获取数据模型列表失败')
  } finally {
    loading.value = false
  }
}

const goToDetail = (model: DataModel) => {
  if (model.status === 'UNINITIALIZED') {
    router.push(`/data-models/${model.id}/init`)
  } else {
    router.push(`/data-models/${model.id}`)
  }
}

const handleCreate = async () => {
  if (!createForm.value.code || !createForm.value.name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creating.value = true
  try {
    await createDataModel(createForm.value)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    createForm.value = { code: '', name: '', description: '', maintainer: '' }
    fetchModels()
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const handleDelete = async (model: DataModel) => {
  try {
    await ElMessageBox.confirm(`确定删除数据模型「${model.name}」？动态表数据将一并删除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteDataModel(model.id!)
    ElMessage.success('删除成功')
    fetchModels()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const statusTagType = (status?: string) => {
  switch (status) {
    case 'READY': return 'success'
    case 'INITIALIZING': return 'warning'
    case 'ERROR': return 'danger'
    default: return 'info'
  }
}

const statusLabel = (status?: string) => {
  switch (status) {
    case 'UNINITIALIZED': return '未初始化'
    case 'INITIALIZING': return '初始化中'
    case 'READY': return '就绪'
    case 'ERROR': return '异常'
    default: return status || '-'
  }
}

onMounted(() => {
  fetchModels()
})
</script>

<style scoped>
.data-model-list-page {
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

.model-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: transform 0.2s;
}

.model-card:hover {
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.model-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  flex-shrink: 0;
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

.card-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
