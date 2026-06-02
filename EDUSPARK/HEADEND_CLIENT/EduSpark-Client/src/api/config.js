const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export {
  BASE_URL
}

export const API_ENDPOINTS = {
  CHAT: '/v1/chat',
  CHAT_STREAM: '/v1/chat/stream',
  CHAT_WITH_FILES: '/v1/chat/with-files',
  CHAT_INTENT: '/v1/chat/intent',
  CHAT_SEARCH: '/v1/chat/search',
  CHAT_WEB_SEARCH: '/v1/chat/web-search',

  CHAT_SESSIONS: '/v1/chat/sessions',
  LESSON_PLAN_DOCUMENTS: '/v1/lesson-plan/documents',
  INTERACTIVE_DOCUMENTS: '/v1/interactive/documents',
  PPT_DOCUMENTS: '/v1/ppt/documents',
  PPT_TEMPLATE_SCENES: '/v1/ppt/scenes',
  PPT_TEMPLATE_STYLES: '/v1/ppt/styles',
  PPT_TEMPLATES: '/v1/ppt/templates',

  KNOWLEDGE_UPLOAD: '/v1/knowledge/upload',
  KNOWLEDGE_COMMON_UPLOAD: '/v1/upload/common',
  KNOWLEDGE_FILES: '/v1/knowledge/files',
  KNOWLEDGE_DELETE: '/v1/knowledge/files',
  KNOWLEDGE_SEARCH: '/v1/knowledge/search',
  KNOWLEDGE_SEARCH_TEST: '/v1/knowledge/search/test',
  KNOWLEDGE_URL: '/v1/upload/url',
  KNOWLEDGE_HEALTH: '/v1/knowledge/health/ollama',
  KNOWLEDGE_WORKSPACES: '/v1/knowledge/workspaces',

  UPLOAD_HEALTH: '/v1/upload/health',

  VOICE_TRANSCRIBE: '/v1/voice/transcribe',
  VOICE_HEALTH: '/v1/voice/health',

  USER_SEND_SMS: '/v1/user/sendSms',
  USER_REGISTER: '/v1/user/register',
  USER_LOGIN: '/v1/user/login',
  USER_INFO: '/v1/user/info',
  USER_UPDATE_INFO: '/v1/user/info',
  USER_CHANGE_PASSWORD: '/v1/user/changePassword',
  USER_FORGOT_PASSWORD: '/v1/user/forgotPassword',
  USER_AVATAR: '/v1/user/avatar',

  INTENT_RULES: '/admin/intent-rules'
}
