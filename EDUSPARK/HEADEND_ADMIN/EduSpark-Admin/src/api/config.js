export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const API_ENDPOINTS = {
  USER_LOGIN: '/v1/user/login',
  USER_INFO: '/v1/user/info',
  PPT_SCENES: '/admin/ppt/scenes',
  PPT_STYLES: '/admin/ppt/styles',
  PPT_TEMPLATES: '/admin/ppt/templates',
  PPT_TEMPLATE_ASSETS: '/admin/ppt/templates/assets',
  PPT_TEMPLATE_PRE_PARSE: '/admin/ppt/templates/pre-parse'
}
