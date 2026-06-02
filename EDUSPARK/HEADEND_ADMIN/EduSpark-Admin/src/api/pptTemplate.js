import { API_ENDPOINTS } from './config.js'
import { del, get, patch, post, postForm, put } from './request.js'

export function listPptScenes(params = {}) {
  return get(API_ENDPOINTS.PPT_SCENES, params)
}

export function createPptScene(data) {
  return post(API_ENDPOINTS.PPT_SCENES, data)
}

export function updatePptScene(id, data) {
  return put(`${API_ENDPOINTS.PPT_SCENES}/${id}`, data)
}

export function deletePptScene(id) {
  return del(`${API_ENDPOINTS.PPT_SCENES}/${id}`)
}

export function listPptStyles(params = {}) {
  return get(API_ENDPOINTS.PPT_STYLES, params)
}

export function createPptStyle(data) {
  return post(API_ENDPOINTS.PPT_STYLES, data)
}

export function updatePptStyle(id, data) {
  return put(`${API_ENDPOINTS.PPT_STYLES}/${id}`, data)
}

export function deletePptStyle(id) {
  return del(`${API_ENDPOINTS.PPT_STYLES}/${id}`)
}

export function listPptTemplates(params = {}) {
  return get(API_ENDPOINTS.PPT_TEMPLATES, params)
}

export function getPptTemplate(id) {
  return get(`${API_ENDPOINTS.PPT_TEMPLATES}/${id}`)
}

export function createPptTemplate(data) {
  return post(API_ENDPOINTS.PPT_TEMPLATES, data)
}

export function updatePptTemplate(id, data) {
  return put(`${API_ENDPOINTS.PPT_TEMPLATES}/${id}`, data)
}

export function togglePptTemplate(id) {
  return patch(`${API_ENDPOINTS.PPT_TEMPLATES}/${id}/toggle`)
}

export function deletePptTemplate(id) {
  return del(`${API_ENDPOINTS.PPT_TEMPLATES}/${id}`)
}

export function uploadPptTemplateAsset(file) {
  const formData = new FormData()
  formData.append('file', file)
  return postForm(API_ENDPOINTS.PPT_TEMPLATE_ASSETS, formData)
}

export function uploadPptTemplateFile(templateId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return postForm(`${API_ENDPOINTS.PPT_TEMPLATES}/${templateId}/file`, formData)
}

/**
 * 在新增/编辑对话框里先把 pptx 上传 + 解析，得到 pendingFileKey + renderConfigJson。
 * 最终保存模板时再把这两个字段一并提交给 createTemplate / updateTemplate。
 */
export function preParsePptTemplate(file) {
  const formData = new FormData()
  formData.append('file', file)
  return postForm(API_ENDPOINTS.PPT_TEMPLATE_PRE_PARSE, formData)
}
