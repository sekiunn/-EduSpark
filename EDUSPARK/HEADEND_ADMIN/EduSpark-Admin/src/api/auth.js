import { API_ENDPOINTS } from './config.js'
import { get, post } from './request.js'

export function loginByPassword(data) {
  return post(API_ENDPOINTS.USER_LOGIN, {
    loginType: 'password',
    phone: data.phone,
    password: data.password
  })
}

export function getCurrentUserInfo() {
  return get(API_ENDPOINTS.USER_INFO)
}
