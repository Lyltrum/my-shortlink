import http from '../axios'
export default {
  // 注册
  addUser(data) {
    return http({
      url: '/user',
      method: 'post',
      data
    })
  },
  // 编辑信息
  editUser(data) {
    return http({
      url: '/user',
      method: 'put',
      data
    })
  },
  // 登录
  login(data) {
    return http({
      url: '/user/login',
      method: 'post',
      data
    })
  },
  // 退出登录（JWT 无状态，客户端丢弃 token 即可）
  logout(data) {
    return http({
      url: '/user/logout?accessToken=' + data.accessToken,
      method: 'delete'
    })
  },
  // 刷新 Access Token
  refreshAccessToken(refreshToken) {
    return http({
      url: '/user/refresh?refreshToken=' + refreshToken,
      method: 'get'
    })
  },
  // 检查用户名是否可用
  hasUsername(data) {
    return http({
      url: '/user/has-username',
      method: 'get',
      params: data
    })
  },
  // 根据用户名查找用户信息
  queryUserInfo(data) {
    return http({
      url: '/actual/user/' + data,
      method: 'get'
    })
  }
}
