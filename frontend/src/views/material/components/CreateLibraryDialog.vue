<template>
  <el-dialog
    :model-value="visible"
    title="创建素材库"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="英文编码" prop="code">
        <el-input v-model="form.code" placeholder="如: employee_list" />
      </el-form-item>
      <el-form-item label="中文名称" prop="name">
        <el-input v-model="form.name" placeholder="如: 员工名册" />
      </el-form-item>
      <el-form-item label="素材库类型" prop="libraryType">
        <el-radio-group v-model="form.libraryType">
          <el-radio value="EXCEL">Excel素材库</el-radio>
          <el-radio value="IMAGE">图片素材库</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="素材库描述" />
      </el-form-item>
      <el-form-item label="维护人" prop="maintainer">
        <el-input v-model="form.maintainer" placeholder="维护人姓名" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createLibrary, type MaterialLibrary } from '@/api/materialLibrary'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  created: []
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<MaterialLibrary>({
  code: '',
  name: '',
  description: '',
  maintainer: '',
  libraryType: 'EXCEL',
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入英文编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '必须以字母开头，只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入中文名称', trigger: 'blur' }],
  libraryType: [{ required: true, message: '请选择素材库类型', trigger: 'change' }],
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    await createLibrary(form)
    ElMessage.success('素材库创建成功')
    emit('created')
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.code = ''
  form.name = ''
  form.description = ''
  form.maintainer = ''
  form.libraryType = 'EXCEL'
  formRef.value?.resetFields()
}
</script>
