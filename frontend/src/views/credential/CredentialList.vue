<template>
  <div class="credential-page">
    <div class="page-header">
      <h2>凭证管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 添加凭证
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="LLM" name="LLM" />
      <el-tab-pane label="数据库" name="DATABASE" />
    </el-tabs>

    <el-table :data="filteredCredentials" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.type === 'LLM' ? 'primary' : 'success'" size="small">
            {{ row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参数" min-width="200">
        <template #default="{ row }">
          <span class="param-preview">{{ getParamPreview(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleTest(row)" :loading="row._testing">
            <el-icon><Connection /></el-icon> 测试
          </el-button>
          <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑凭证' : '添加凭证'" width="600px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="凭证名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" :disabled="isEdit" style="width: 100%">
            <el-option label="LLM" value="LLM" />
            <el-option label="Database" value="DATABASE" />
          </el-select>
        </el-form-item>

        <!-- LLM params -->
        <template v-if="form.type === 'LLM'">
          <el-form-item label="API 地址" required>
            <el-input v-model="form.params.apiUrl" placeholder="https://api.openai.com" />
          </el-form-item>
          <el-form-item label="API 密钥" required>
            <el-input v-model="form.params.apiKey" type="password" show-password placeholder="sk-..." />
          </el-form-item>
          <el-form-item label="组织">
            <el-input v-model="form.params.organization" placeholder="选填" />
          </el-form-item>
        </template>

        <!-- Database params -->
        <template v-if="form.type === 'DATABASE'">
          <el-form-item label="JDBC URL" required>
            <el-input v-model="form.params.jdbcUrl" placeholder="jdbc:mysql://host:3306/db" />
          </el-form-item>
          <el-form-item label="驱动类" required>
            <el-input v-model="form.params.driverClass" placeholder="com.mysql.cj.jdbc.Driver" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="form.params.username" placeholder="选填" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.params.password" type="password" show-password placeholder="选填" />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="handleTestDirect" :loading="testLoading">测试连接</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saveLoading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCredentials, createCredential, updateCredential, deleteCredential,
  testCredential, testCredentialDirect,
  type CredentialDTO, type CredentialCreateDTO
} from '@/api/credential'

const loading = ref(false)
const credentials = ref<CredentialDTO[]>([])
const activeTab = ref('all')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const saveLoading = ref(false)
const testLoading = ref(false)

const defaultForm = (): CredentialCreateDTO => ({
  name: '',
  type: 'LLM',
  params: { apiUrl: '', apiKey: '', organization: '', jdbcUrl: '', driverClass: '', username: '', password: '' }
})
const form = ref<CredentialCreateDTO>(defaultForm())

const filteredCredentials = computed(() => {
  if (activeTab.value === 'all') return credentials.value
  return credentials.value.filter(c => c.type === activeTab.value)
})

function getParamPreview(cred: CredentialDTO): string {
  if (cred.type === 'LLM') {
    return `${cred.params.apiUrl || 'N/A'}`
  }
  return `${cred.params.jdbcUrl || 'N/A'}`
}

function formatDate(dateStr: string): string {
  return dateStr ? new Date(dateStr).toLocaleString() : ''
}

async function loadCredentials() {
  loading.value = true
  try {
    credentials.value = await getCredentials()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleTabChange() { /* filter is computed, no action needed */ }

function openCreateDialog() {
  isEdit.value = false
  editId.value = null
  form.value = defaultForm()
  dialogVisible.value = true
}

function openEditDialog(cred: CredentialDTO) {
  isEdit.value = true
  editId.value = cred.id
  form.value = {
    name: cred.name,
    type: cred.type,
    params: { ...defaultForm().params, ...cred.params }
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name) {
    ElMessage.warning('请输入名称')
    return
  }
  saveLoading.value = true
  try {
    // Clean params based on type
    const params: Record<string, any> = {}
    if (form.value.type === 'LLM') {
      params.apiUrl = form.value.params.apiUrl
      params.apiKey = form.value.params.apiKey
      if (form.value.params.organization) params.organization = form.value.params.organization
    } else {
      params.jdbcUrl = form.value.params.jdbcUrl
      params.driverClass = form.value.params.driverClass
      if (form.value.params.username) params.username = form.value.params.username
      if (form.value.params.password) params.password = form.value.params.password
    }

    const data: CredentialCreateDTO = { name: form.value.name, type: form.value.type, params }

    if (isEdit.value && editId.value) {
      await updateCredential(editId.value, data)
      ElMessage.success('凭证已更新')
    } else {
      await createCredential(data)
      ElMessage.success('凭证已创建')
    }
    dialogVisible.value = false
    await loadCredentials()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    saveLoading.value = false
  }
}

async function handleDelete(cred: CredentialDTO) {
  try {
    await ElMessageBox.confirm(`确认删除凭证 "${cred.name}"？`, '确认', { type: 'warning' })
    await deleteCredential(cred.id)
    ElMessage.success('已删除')
    await loadCredentials()
  } catch { /* cancelled */ }
}

async function handleTest(cred: CredentialDTO) {
  cred._testing = true
  try {
    const result = await testCredential(cred.id)
    ElMessage.success(result)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    cred._testing = false
  }
}

async function handleTestDirect() {
  testLoading.value = true
  try {
    const params: Record<string, any> = {}
    if (form.value.type === 'LLM') {
      params.apiUrl = form.value.params.apiUrl
      params.apiKey = form.value.params.apiKey
    } else {
      params.jdbcUrl = form.value.params.jdbcUrl
      params.driverClass = form.value.params.driverClass
      if (form.value.params.username) params.username = form.value.params.username
      if (form.value.params.password) params.password = form.value.params.password
    }
    const result = await testCredentialDirect(form.value.type, params)
    ElMessage.success(result)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    testLoading.value = false
  }
}

onMounted(loadCredentials)
</script>

<style scoped>
.credential-page {
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.param-preview {
  color: #909399;
  font-size: 13px;
}
</style>
