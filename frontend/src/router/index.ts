import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'

const routes = [
  {
    path: '/',
    component: AppLayout,
    children: [
      { path: '', redirect: '/material/libraries' },
      {
        path: '/material/libraries',
        name: 'MaterialLibraryList',
        component: () => import('@/views/material/MaterialLibraryList.vue'),
        meta: { title: '素材库' },
      },
      {
        path: '/material/libraries/:id',
        name: 'MaterialLibraryDetail',
        component: () => import('@/views/material/MaterialLibraryDetail.vue'),
        meta: { title: '素材详情' },
      },
      {
        path: '/credentials',
        name: 'CredentialList',
        component: () => import('@/views/credential/CredentialList.vue'),
        meta: { title: '凭证管理' },
      },
      {
        path: '/ai/agents',
        name: 'AiAgentList',
        component: () => import('@/views/ai/AiAgentList.vue'),
        meta: { title: 'AI 助手' },
      },
      {
        path: '/ai/chat/:agentId',
        name: 'ChatView',
        component: () => import('@/views/ai/ChatView.vue'),
        meta: { title: 'AI 对话' },
      },
      {
        path: '/ocr',
        name: 'OcrView',
        component: () => import('@/views/ocr/OcrView.vue'),
        meta: { title: 'OCR 图片解析' },
      },
      {
        path: '/data-models',
        name: 'DataModelList',
        component: () => import('@/views/dataModel/DataModelList.vue'),
        meta: { title: '数据模型' },
      },
      {
        path: '/data-models/:id/init',
        name: 'DataModelWizard',
        component: () => import('@/views/dataModel/DataModelWizard.vue'),
        meta: { title: '模型初始化' },
      },
      {
        path: '/data-models/:id',
        name: 'DataModelDetail',
        component: () => import('@/views/dataModel/DataModelDetail.vue'),
        meta: { title: '模型详情' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  document.title = (to.meta.title as string) || '财务报销审查系统'
})

export default router
