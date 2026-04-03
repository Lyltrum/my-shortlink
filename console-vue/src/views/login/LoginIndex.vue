<template>
  <div class="login-page">
    <div class="title-area">
      <div class="title-icon">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="36" height="36">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <h1 class="title">短链接生成平台</h1>
      <p class="subtitle">高效管理您的短链接，轻松追踪访问数据</p>
    </div>
    <div class="login-box">
      <!-- 登录 -->
      <div class="logon" :class="{ hidden: !isLogin }">
        <h2>用户登录</h2>
        <el-form ref="loginFormRef1" :model="loginForm" label-width="50px" :rules="loginFormRule">
          <div class="form-container1">
            <el-form-item prop="phone">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" maxlength="11" show-word-limit clearable>
                <template v-slot:prepend> 用户名 </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" clearable placeholder="请输入密码" show-password
                style="margin-top: 20px">
                <template v-slot:prepend> 密<span class="second-font">码</span> </template>
              </el-input>
            </el-form-item>
          </div>
          <div class="btn-gourp">
            <div>
              <el-checkbox class="remeber-password" v-model="checked"
                style="color: #a0a0a0; margin: 0 0 0px 0">记住密码</el-checkbox>
            </div>
            <div>
              <el-button :loading="loading" @keyup.enter="login" type="primary" plain
                @click="login(loginFormRef1)">登录</el-button>
            </div>
          </div>
        </el-form>
      </div>
      <!-- 注册 -->
      <div class="register" :class="{ hidden: isLogin }">
        <h2>用户注册</h2>
        <el-form ref="loginFormRef2" :model="addForm" label-width="50px" class="form-container" width="width"
          :rules="addFormRule">
          <el-form-item prop="username">
            <el-input v-model="addForm.username" placeholder="请输入用户名" maxlength="11" show-word-limit clearable>
              <template v-slot:prepend> 用户名 </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="mail">
            <el-input v-model="addForm.mail" placeholder="请输入邮箱" show-word-limit clearable>
              <template v-slot:prepend> 邮<span class="second-font">箱</span> </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="addForm.phone" placeholder="请输入手机号" show-word-limit clearable>
              <template v-slot:prepend> 手机号 </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="realName">
            <el-input v-model="addForm.realName" placeholder="请输入姓名" show-word-limit clearable>
              <template v-slot:prepend> 姓<span class="second-font">名</span> </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input v-model="addForm.password" type="password" clearable placeholder="请输入密码" show-password>
              <template v-slot:prepend> 密<span class="second-font">码</span> </template>
            </el-input>
          </el-form-item>
          <!-- 验证码 -->
          <!-- <el-form-item prop="vertify_code">
            <el-input
              v-model="loginForm.vertify_code"
              placeholder="验证码"
              prefix-icon="el-icon-key"
              clearable
            >
              <template v-slot:append>
                <div class="login-code" @click="refreshCode" title="看不清？点击切换">
                  <vertify-code :identifyCode="loginIdentifyCode"></vertify-code>
                </div>
              </template>
            </el-input>
          </el-form-item> -->
          <div class="btn-gourp">
            <div></div>
            <div>
              <el-button :loading="loading" @keyup.enter="login" type="primary" plain
                @click="addUser(loginFormRef2)">注册</el-button>
            </div>
          </div>
        </el-form>
      </div>
      <!-- 左右移动的切换按钮 -->
      <div class="move" ref="moveRef">
        <span style="font-size: 18px; margin-bottom: 25px; color: rgb(225, 238, 250)">{{
          !isLogin ? '已有账号？' : '还没有账号？'
        }}</span>
        <span style="font-size: 16px; color: rgb(225, 238, 250)">{{
          !isLogin ? '欢迎登录账号！' : '欢迎注册账号！'
        }}</span>
        <el-button style="width: 100px; margin-top: 30px" @click="changeLogin">{{
          !isLogin ? '去登录' : '去注册'
        }}</el-button>
      </div>
    </div>
    <div ref="vantaRef" class="vanta"></div>
  </div>
  <el-dialog v-model="isWC" title="人机验证" width="40%" :before-close="handleClose">
    <div class="verification-flex">
      <span>扫码下方二维码，关注后回复：<strong><span style="color:blue;">link</span></strong>，获取拿个offer-SaaS短链接系统人机验证码</span>
      <img class="img" src="@/assets/png/公众号二维码.png" alt="">
      <el-form class="form" :model="verification" :rules="verificationRule" ref="verificationRef">
        <el-form-item prop="code" label="验证码">
          <el-input v-model="verification.code" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="isWC = false">取消</el-button>
        <el-button type="primary" @click="verificationLogin(verificationRef)">
          确认
        </el-button>
      </span>
    </template>
  </el-dialog>
  <!-- </template> -->
</template>

<script setup>
import { setToken, setUsername, getUsername, setRefreshToken } from '@/core/auth.js'
import { ref, reactive, onMounted, onBeforeUnmount, watch, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as THREE from 'three'
import WAVES from 'vanta/src/vanta.waves'
const { proxy } = getCurrentInstance()
const API = proxy.$API
const loginFormRef1 = ref()
const loginFormRef2 = ref()
const router = useRouter()
const loginForm = reactive({
  username: 'admin',
  password: 'admin123456',
})
const addForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  mail: ''
})

const addFormRule = reactive({
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^1[3|5|7|8|9]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: 'blur'
    },
    { min: 11, max: 11, message: '手机号必须是11位', trigger: 'blur' }
  ],
  username: [{ required: true, message: '请输入您的真实姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 15, message: '密码长度请在八位以上', trigger: 'blur' }
  ],
  mail: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      pattern: /^([a-zA-Z]|[0-9])(\w|\-)+@[a-zA-Z0-9]+\.([a-zA-Z]{2,4})$/,
      message: '请输入正确的邮箱号',
      trigger: 'blur'
    }
  ],
  realNamee: [
    { required: true, message: '请输姓名', trigger: 'blur' },
  ]
})
const loginFormRule = reactive({
  username: [{ required: true, message: '请输入您的真实姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 15, message: '密码长度请在八位以上', trigger: 'blur' }
  ],
})
// 注册
const addUser = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      // 检测用户名是否已经存在
      const res1 = await API.user.hasUsername({ username: addForm.username })
      if (res1.data.success !== false) {
        // 注册
        const res2 = await API.user.addUser(addForm)
        // console.log(res2)
        if (res2.data.success === false) {
          ElMessage.warning(res2.data.message)
        } else {
          const res3 = await API.user.login({ username: addForm.username, password: addForm.password })
          const { accessToken, refreshToken } = res3?.data?.data || {}
          if (accessToken) {
            setToken(accessToken)
            setRefreshToken(refreshToken)
            setUsername(addForm.username)
            localStorage.setItem('token', accessToken)
            localStorage.setItem('refresh_token', refreshToken)
            localStorage.setItem('username', addForm.username)
          }
          ElMessage.success('注册登录成功！')
          router.push('/home')
        }
      } else {
        ElMessage.warning('用户名已存在！')
      }
    } else {
      return false
    }
  })

}
// 公众号验证码
const isWC = ref(false)
const verificationRef = ref()
const verification = reactive({
  code: ''
})
const verificationRule = reactive({
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
})
const verificationLogin = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      const tempPassword = loginForm.password
      loginForm.password = verification.code
      const res1 = await API.user.login(loginForm)
      if (res1.data.code === '0') {
        const { accessToken, refreshToken } = res1?.data?.data || {}
        if (accessToken) {
          setToken(accessToken)
          setRefreshToken(refreshToken)
          setUsername(loginForm.username)
          localStorage.setItem('token', accessToken)
          localStorage.setItem('refresh_token', refreshToken)
          localStorage.setItem('username', loginForm.username)
        }
        ElMessage.success('登录成功！')
        router.push('/home')
      } else if (res1.data.message === '用户已登录') {
        // 如果已经登录了，判断一下浏览器保存的登录信息是不是再次登录的信息，如果是就正常登录
        const cookiesUsername = getUsername()
        if (cookiesUsername === loginForm.username) {
          ElMessage.success('登录成功！')
          router.push('/home')
        } else {
          ElMessage.warning('用户已在别处登录，请勿重复登录！')
        }
      } else {
        ElMessage.error('请输入正确的验证码!')
      }
      loginForm.password = tempPassword
    }
  })
}
// 登录
const login = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      const res1 = await API.user.login(loginForm)
      if (res1.data.code === '0') {
        const { accessToken, refreshToken } = res1?.data?.data || {}
        if (accessToken) {
          setToken(accessToken)
          setRefreshToken(refreshToken)
          setUsername(loginForm.username)
          localStorage.setItem('token', accessToken)
          localStorage.setItem('refresh_token', refreshToken)
          localStorage.setItem('username', loginForm.username)
        }
        ElMessage.success('登录成功！')
        router.push('/home')
      } else if (res1.data.message === '用户已登录') {
        // 如果已经登录了，判断一下浏览器保存的登录信息是不是再次登录的信息，如果是就正常登录
        const cookiesUsername = getUsername()
        if (cookiesUsername === loginForm.username) {
          ElMessage.success('登录成功！')
          router.push('/home')
        } else {
          ElMessage.warning('用户已在别处登录，请勿重复登录！')
        }
      } else if (res1.data.message === '用户不存在') {
        ElMessage.error('请输入正确的账号密码!')
      }
    } else {
      return false
    }
  })


}

const loading = ref(false)
// 是否记住密码
const checked = ref(true)
const vantaRef = ref()
// 动态背景
let vantaEffect = null
onMounted(() => {
  vantaEffect = WAVES({
    el: vantaRef.value,
    THREE: THREE,
    mouseControls: true,
    touchControls: true,
    gyroControls: false,
    minHeight: 200.0,
    minWidth: 200.0,
    scale: 1.0,
    scaleMobile: 1.0
  })
})
onBeforeUnmount(() => {
  if (vantaEffect) {
    vantaEffect.destroy()
  }
})
// 展示登录还是展示注册
const isLogin = ref(true)
const moveRef = ref() // 左右移动的切换按钮模块
const changeLogin = () => {
  let domain = window.location.host
  if (domain === 'shortlink.magestack.cn' || domain === 'shortlink.lu.com') {
    ElMessage.warning('演示环境暂不支持注册')
    return
  }
  isLogin.value = !isLogin.value
  if (isLogin.value) {
    moveRef.value.style.transform = 'translate(0, 0)'
  } else {
    moveRef.value.style.transform = 'translate(-420px, 0)'
  }
}
</script>

<style lang="less" scoped>
.login-box {
  border: none;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  border-radius: 24px;
  padding: 0 40px 0 40px;
  width: 720px;
  position: absolute;
  z-index: 999;
  top: 58%;
  left: 50%;
  transform: translate(-50%, -50%);
  box-sizing: border-box;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.18), 0 0 0 1px rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.97);
  backdrop-filter: blur(20px);
  animation: hideIndex 0.5s;

  h2 {
    font-size: 26px;
    font-family:
      PingFangSC-Semibold,
      PingFang SC;
    font-weight: 700;
    color: #1a1f36;
    width: 100%;
    text-align: center;
    padding: 24px 0 18px;
    letter-spacing: 1px;
  }

  .el-form-item {
    margin-bottom: 18px;
  }

  .btn-gourp {
    margin-top: 24px;
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;

    .el-button {
      width: 100px;
    }

    .remeber-password {
      left: 0;
      line-height: 0.5rem;
    }
  }

  .el-checkbox {
    width: 100%;
    text-align: center;
    margin-top: 1rem;
  }
}

/deep/ .el-form-item__content {
  margin-left: 0 !important;
}

@keyframes hideIndex {

  // <!--具体细节自己可以调整-->
  0% {
    opacity: 0;
    transform: translate(7.3125rem, -50%);
  }

  100% {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}

.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.vanta {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  bottom: 0;
  z-index: 0;
}

.logon {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.hidden {
  animation: hidden 1s;
  animation-fill-mode: forwards; // 保持最后的状态
}

@keyframes hidden {

  // <!--具体细节自己可以调整-->
  0% {
    opacity: 1;
  }

  70% {
    opacity: 0;
  }

  100% {
    opacity: 0;
  }
}

.move {
  position: absolute;
  right: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 40%;
  transition-duration: 0.5s;
  align-items: center;
  background: linear-gradient(160deg, #667eea 0%, #0984e3 60%, #06beb6 100%);
  box-shadow: -4px 0 20px rgba(9, 132, 227, 0.15);
}

.title-area {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: 13%;
  z-index: 999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.title-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #667eea 0%, #0984e3 100%);
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(9, 132, 227, 0.4);
  margin-bottom: 4px;
}

.title {
  font-size: 36px;
  color: #fff;
  font-weight: 800;
  letter-spacing: 3px;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.25);
}

.subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
  letter-spacing: 1px;
  font-weight: 400;
}

:deep(.el-input__suffix-inner) {
  width: 60px;
}

.form-container1 {
  transform: translateY(-80%);
}

.second-font {
  margin-left: 13px;
}

.verification-flex {
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  .img {
    margin-top: 10px;
    align-self: center;
  }
  .form {
    transform: translateY(15px);
    width: 90%;
  }
}
</style>
