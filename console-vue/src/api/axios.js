import axios from 'axios'
import { getToken, getUsername, getRefreshToken, setToken, setRefreshToken, clearAll } from '@/core/auth.js'
import { isNotEmpty } from '@/utils/plugins.js'
import router from "@/router"
import { ElMessage } from 'element-plus'
import userApi from '@/api/modules/user'

const baseURL = '/api/short-link/admin/v1'

const http = axios.create({
  baseURL: baseURL,
  timeout: 15000
})

let isRefreshing = false
let refreshSubscribers = []

function subscribeTokenRefresh(cb) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(newAccessToken, newRefreshToken) {
  refreshSubscribers.forEach(cb => cb(newAccessToken, newRefreshToken))
  refreshSubscribers = []
}

http.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (isNotEmpty(token)) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    config.headers['Username'] = isNotEmpty(getUsername()) ? getUsername() : ''
    return config
  },
  (error) => Promise.reject(error)
)

http.interceptors.response.use(
  (res) => {
    if (res.status == 0 || res.status == 200) {
      return Promise.resolve(res)
    }
    return Promise.reject(res)
  },
  async (err) => {
    const originalRequest = err.config

    if (err.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((newAccessToken, newRefreshToken) => {
            originalRequest.headers['Authorization'] = 'Bearer ' + newAccessToken
            setToken(newAccessToken)
            setRefreshToken(newRefreshToken)
            resolve(http(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshToken = getRefreshToken()
        if (!refreshToken) {
          throw new Error('No refresh token')
        }
        const res = await userApi.refreshAccessToken(refreshToken)
        const { accessToken, refreshToken: newRefreshToken } = res.data.data
        setToken(accessToken)
        setRefreshToken(newRefreshToken)
        localStorage.setItem('token', accessToken)
        localStorage.setItem('refresh_token', newRefreshToken)

        originalRequest.headers['Authorization'] = 'Bearer ' + accessToken
        onTokenRefreshed(accessToken, newRefreshToken)
        isRefreshing = false

        return http(originalRequest)
      } catch (refreshError) {
        isRefreshing = false
        clearAll()
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login')
        return Promise.reject(refreshError)
      }
    }

    if (err.response?.status === 401) {
      clearAll()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

export default http
