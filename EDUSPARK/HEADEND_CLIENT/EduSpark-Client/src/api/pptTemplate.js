import { API_ENDPOINTS } from './config.js'
import { get } from './request.js'

export function getPptTemplateScenes() {
  return get(API_ENDPOINTS.PPT_TEMPLATE_SCENES)
}

export function getPptTemplateStyles(sceneId = null) {
  return get(API_ENDPOINTS.PPT_TEMPLATE_STYLES, {
    ...(sceneId != null ? { sceneId } : {})
  })
}

export function getPptTemplates(params = {}) {
  return get(API_ENDPOINTS.PPT_TEMPLATES, params)
}

export function getPptTemplateDetail(id) {
  return get(`${API_ENDPOINTS.PPT_TEMPLATES}/${id}`)
}
