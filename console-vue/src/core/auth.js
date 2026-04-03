import Cookies from 'js-cookie'

const TokenKey = 'token'
const RefreshTokenKey = 'refresh_token'

export function getToken() {
  return Cookies.get(TokenKey)
}
export function getUsername() {
  return Cookies.get('username')
}

export function setToken(token) {
  return Cookies.set(TokenKey, token)
}

export function setUsername(username) {
  return Cookies.set('username', username)
}

export function setRefreshToken(token) {
  return Cookies.set(RefreshTokenKey, token)
}

export function getRefreshToken() {
  return Cookies.get(RefreshTokenKey)
}

export function removeKey() {
  Cookies.remove(TokenKey)
  Cookies.remove(RefreshTokenKey)
}

export function removeUsername() {
  return Cookies.remove('username')
}

export function clearAll() {
  removeKey()
  removeUsername()
  localStorage.removeItem('token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('username')
}


