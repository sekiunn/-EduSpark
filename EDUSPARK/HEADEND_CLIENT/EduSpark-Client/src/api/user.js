/**
 * 用户相关 API
 */
import { get, post, put, postForm } from './request.js'
import { API_ENDPOINTS } from './config.js'

/**
 * 发送短信验证码
 * @param {string} phone - 手机号
 * @param {string} scene - 场景：register / login / forgotPassword
 * @returns {Promise}
 */
export function sendSmsCode(phone, scene) {
  return post(API_ENDPOINTS.USER_SEND_SMS, { phone, scene })
}

/**
 * 用户注册
 * @param {object} data - { phone, smsCode, password, confirmPassword, username }
 * @returns {Promise}
 */
export function register(data) {
  return post(API_ENDPOINTS.USER_REGISTER, data)
}

/**
 * 用户登录
 * @param {object} data - { loginType: 'password'|'sms', phone, password?, smsCode? }
 * @returns {Promise}
 */
export function login(data) {
  return post(API_ENDPOINTS.USER_LOGIN, data)
}

/**
 * 忘记密码
 * @param {object} data - { phone, smsCode, newPassword }
 * @returns {Promise}
 */
export function forgotPassword(data) {
  return post(API_ENDPOINTS.USER_FORGOT_PASSWORD, data)
}

/**
 * 获取当前用户信息
 * @returns {Promise}
 */
export function getUserInfo() {
  return get(API_ENDPOINTS.USER_INFO)
}

/**
 * 更新用户信息
 * @param {object} data - { username?, email?, bio?, avatar? }
 * @returns {Promise}
 */
export function updateUserInfo(data) {
  return put(API_ENDPOINTS.USER_UPDATE_INFO, data)
}

/**
 * 修改密码
 * @param {object} data - { oldPassword, newPassword }
 * @returns {Promise}
 */
export function changePassword(data) {
  return post(API_ENDPOINTS.USER_CHANGE_PASSWORD, data)
}

/**
 * 上传头像
 * @param {File} file - 图片文件
 * @returns {Promise<string>} 返回头像URL
 */
export async function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await postForm(API_ENDPOINTS.USER_AVATAR, formData)
  return res.data
}
