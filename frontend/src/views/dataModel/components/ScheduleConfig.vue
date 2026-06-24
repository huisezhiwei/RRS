<template>
  <div class="schedule-config">
    <el-form :model="config" label-width="100px" style="max-width: 500px">
      <el-form-item label="启用调度">
        <el-switch v-model="config.enabled" />
      </el-form-item>
      <el-form-item label="Cron 表达式" v-if="config.enabled">
        <el-input v-model="config.cronExpression" placeholder="0 0 2 * * ?">
          <template #append>
            <el-dropdown @command="setPreset">
              <el-button>预设</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="0 0 2 * * ?">每天凌晨2点</el-dropdown-item>
                  <el-dropdown-item command="0 0 8 * * ?">每天早8点</el-dropdown-item>
                  <el-dropdown-item command="0 0 */6 * * ?">每6小时</el-dropdown-item>
                  <el-dropdown-item command="0 0 2 ? * MON">每周一凌晨2点</el-dropdown-item>
                  <el-dropdown-item command="0 */30 * * * ?">每30分钟</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="抽取范围" v-if="config.enabled">
        <el-radio-group v-model="config.scopeType">
          <el-radio value="FULL">全量</el-radio>
          <el-radio value="INCREMENTAL">增量</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="config.enabled">
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <el-button type="danger" @click="handleDelete" v-if="config.id">删除调度</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveSchedule, deleteSchedule, type ScheduleConfig as ScheduleType } from '@/api/dataModel'

const props = defineProps<{
  modelId: number
  schedule?: ScheduleType | null
}>()

const emit = defineEmits<{
  'updated': []
}>()

const config = ref({
  id: null as number | null,
  cronExpression: '0 0 2 * * ?',
  enabled: false,
  scopeType: 'INCREMENTAL',
})

const saving = ref(false)

watch(() => props.schedule, (val) => {
  if (val) {
    config.value = {
      id: val.id || null,
      cronExpression: val.cronExpression || '0 0 2 * * ?',
      enabled: val.enabled || false,
      scopeType: val.scopeType || 'INCREMENTAL',
    }
  }
}, { immediate: true })

const setPreset = (cron: string) => {
  config.value.cronExpression = cron
}

const handleSave = async () => {
  saving.value = true
  try {
    await saveSchedule(props.modelId, {
      cronExpression: config.value.cronExpression,
      enabled: config.value.enabled,
      scopeType: config.value.scopeType,
    })
    ElMessage.success('调度配置已保存')
    emit('updated')
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定删除定时调度配置？', '确认', { type: 'warning' })
    await deleteSchedule(props.modelId)
    ElMessage.success('已删除')
    config.value = { id: null, cronExpression: '0 0 2 * * ?', enabled: false, scopeType: 'INCREMENTAL' }
    emit('updated')
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.message || '删除失败')
  }
}
</script>

<style scoped>
.schedule-config {
  padding: 12px 0;
}
</style>
