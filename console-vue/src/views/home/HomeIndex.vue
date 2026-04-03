<template>
  <div class="common-layout">
    <el-container>
      <el-header height="56px" style="padding: 0">
        <div class="header">
          <div @click="toMySpace" class="logo">
            <div class="logo-icon">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="18" height="18">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" stroke="white" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" stroke="white" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <span class="logo-text">短链接生成平台</span>
          </div>
          <div style="display: flex; align-items: center">
            <el-dropdown>
              <div class="block">
                <div class="user-avatar">{{ avatarLetter }}</div>
                <span class="name-span" style="text-decoration: none">{{username}}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="toMine">
                    <el-icon style="margin-right:6px"><User /></el-icon>个人信息
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="logout">
                    <el-icon style="margin-right:6px"><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      <el-main style="padding: 0">
        <div class="content-box">
          <RouterView class="content-space" />
        </div>
      </el-main>
      <!-- <el-container>
        <el-aside width="180px">
          <el-menu
            active-text-color="#073372"
            background-color="#0e5782"
            class="el-menu-vertical-demo"
            :default-active="getLasteRoute(route.path)"
            text-color="#fff"
            @select="handleSelect"
          >
            <template v-for="item in menuInfos" :key="item.name">
              <el-menu-item :index="item.path">
                <el-icon><icon-menu /></el-icon>
                <span>{{ item.name }}</span>
              </el-menu-item>
            </template>
          </el-menu></el-aside
        >

      </el-container> -->
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, getCurrentInstance, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { clearAll, getToken } from '@/core/auth.js'
import { ElMessage } from 'element-plus'
import { User, SwitchButton } from '@element-plus/icons-vue'
const { proxy } = getCurrentInstance()
const API = proxy.$API
// 当当前路径和菜单不匹配时，菜单不会被选中
const router = useRouter()
const squareUrl = ref('https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png')
const toMine = () => {
  router.push('/home' + '/account')
}
// 登出
const logout = async () => {
  const accessToken = getToken()
  await API.user.logout({ accessToken })
  clearAll()
  router.push('/login')
  ElMessage.success('成功退出！')
}
// 点击左上方的图片跳转到我的空间
const toMySpace = () => {
  router.push('/home' + '/space')
}
const username = ref('')
const avatarLetter = computed(() => username.value ? username.value[0].toUpperCase() : 'U')
onMounted(async () => {
  const actualUsername = getUsername()
  const res = await API.user.queryUserInfo(actualUsername)
  username.value = truncateText(actualUsername, 8)
})
const extractColorByName = (name) => {
  var temp = []
  temp.push('#')
  for (let index = 0; index < name.length; index++) {
    temp.push(parseInt(name[index].charCodeAt(0), 10).toString(16))
  }
  return temp.slice(0, 5).join('').slice(0, 4)
}

// 辅助函数，用于截断文本
const truncateText = (text, maxLength) => {
  return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
}
</script>

<style lang="scss" scoped>
.el-container {
  height: 100vh;

  .el-aside {
    border: 0;
    background-color: #0e5782;

    ul {
      border: 0px;
    }
  }

  .el-main {
    background-color: #e8e8e8;
  }
}

.header {
  color: rgba(0,0,0,.85);
  background: linear-gradient(90deg, #1a1f36 0%, #252b30 100%);
  border-bottom: 1px solid rgba(255,255,255,0.05);
  padding: 0 20px 0 20px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .block {
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 10px;
    border: 0px;
    padding: 6px 12px;
    border-radius: 8px;
    transition: background 0.2s;

    &:hover {
      background: rgba(255,255,255,0.08);
    }
  }
}

.content-box {
  height: calc(100vh - 50px);
  background-color: white;
}

:deep(.el-tooltip__trigger:focus-visible) {
  outline: unset;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: rgba(255,255,255,0.06);

    .logo-text {
      color: #fff;
    }
  }
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #667eea 0%, #0984e3 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(9,132,227,0.4);
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: #e8e8e8;
  font-family: 'PingFang SC', 'Microsoft YaHei', Helvetica, Arial, sans-serif;
  letter-spacing: 0.5px;
  transition: color 0.2s;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #0984e3 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(9,132,227,0.35);
}

.link-span {
  color: #fff;
  opacity: .6;
  margin-right: 30px;
  font-size: 16px;
  font-family: 'Helvetica Neue', Helvetica, STHeiTi, Arial, sans-serif;
  cursor: pointer;
  text-decoration: none;
}

.link-span:hover {
  text-decoration: underline !important;
  opacity: 1;
  color: #fff;
}

.name-span {
  color: rgba(255,255,255,0.75);
  font-size: 13px;
  font-family: 'PingFang SC', 'Microsoft YaHei', Helvetica, Arial, sans-serif;
  cursor: pointer;
  text-decoration: none;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-weight: 500;
}

.avatar {
  transform: translateY(-2px);
}
</style>
