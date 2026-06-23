<template>
  <el-container class="app-layout">
    <el-header class="app-header">
      <div class="header-left">
        <el-icon :size="28" color="#409eff"><Document /></el-icon>
        <h1 class="app-title">{{ appTitle }}</h1>
      </div>
      <div class="header-nav">
        <el-menu mode="horizontal" :default-active="activeMenu" router :ellipsis="false">
          <el-menu-item index="/material/libraries">
            <el-icon><FolderOpened /></el-icon>
            素材管理
          </el-menu-item>
          <el-menu-item index="/credentials">
            <el-icon><Key /></el-icon>
            凭证管理
          </el-menu-item>
          <el-menu-item index="/ai/agents">
            <el-icon><ChatDotRound /></el-icon>
            AI 助手
          </el-menu-item>
        </el-menu>
      </div>
    </el-header>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const appTitle = import.meta.env.VITE_APP_TITLE || '财务报销审查系统'

const activeMenu = computed(() => {
  if (route.path.startsWith('/material')) return '/material/libraries'
  if (route.path.startsWith('/credentials')) return '/credentials'
  if (route.path.startsWith('/ai')) return '/ai/agents'
  return route.path
})
</script>

<style scoped>
.app-layout {
  height: 100%;
  background-color: #f5f7fa;
}

.app-header {
  display: flex;
  align-items: center;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  padding: 0 24px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-right: 40px;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.header-nav {
  flex: 1;
}

.header-nav .el-menu {
  border-bottom: none;
}

.app-main {
  padding: 24px;
  overflow-y: auto;
}
</style>
