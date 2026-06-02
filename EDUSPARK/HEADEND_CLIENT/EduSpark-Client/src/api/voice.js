/**
 * 语音相关 API
 */
import { BASE_URL } from './config.js'
import { API_ENDPOINTS } from './config.js'

/**
 * 语音转文字（Whisper）
 * @param {Blob/File} audioFile - 音频文件
 * @returns {Promise} 转写结果
 */
export function transcribeAudio(audioFile) {
  const formData = new FormData()
  formData.append('file', audioFile, 'recording.webm')
  formData.append('model', 'whisper')
  formData.append('language', 'zh')

  const token = localStorage.getItem('eduspark_token')
  const headers = token ? { 'Authorization': `Bearer ${token}` } : {}

  return fetch(BASE_URL + API_ENDPOINTS.VOICE_TRANSCRIBE, {
    method: 'POST',
    headers,
    body: formData
  }).then(res => {
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return res.json()
  })
}

/**
 * 录音状态
 */
export const RecordingState = {
  IDLE: 'idle',
  RECORDING: 'recording',
  PROCESSING: 'processing',
  DONE: 'done',
  ERROR: 'error'
}

/**
 * 创建录音管理器
 */
export function createRecorder(onStateChange = () => {}, onTranscript = () => {}) {
  let mediaRecorder = null
  let audioChunks = []
  let state = RecordingState.IDLE
  let durationTimer = null
  let stream = null

  const setState = (newState) => {
    state = newState
    onStateChange(newState)
  }

  /**
   * 开始录音
   */
  const startRecording = async () => {
    try {
      // 请求麦克风权限
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })

      // 创建 MediaRecorder
      const options = { mimeType: 'audio/webm;codecs=opus' }
      if (!MediaRecorder.isTypeSupported(options.mimeType)) {
        options.mimeType = 'audio/webm'
      }

      mediaRecorder = new MediaRecorder(stream, options)
      audioChunks = []

      // 收集音频数据
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          audioChunks.push(e.data)
        }
      }

      // 录音停止
      mediaRecorder.onstop = () => {
        // 只停止轨道，识别逻辑交给 stopRecording 中的立即触发
        if (stream) {
          stream.getTracks().forEach(track => track.stop())
        }
      }

      // 开始录音
      mediaRecorder.start(1000) // 每秒收集一次数据
      setState(RecordingState.RECORDING)

    } catch (error) {
      console.error('无法访问麦克风:', error)
      setState(RecordingState.ERROR)
      throw error
    }
  }

  /**
   * 停止录音
   */
  const stopRecording = () => {
    if (mediaRecorder && mediaRecorder.state === 'recording') {
      mediaRecorder.stop()
    }
    // 立即触发识别，onstop 仅负责清理流
    setTimeout(() => {
      const audioBlob = new Blob(audioChunks, { type: 'audio/webm' })
      recognizeAudio(audioBlob)
        .then(text => {
          onTranscript(text)
          setState(RecordingState.DONE)
        })
        .catch(() => {
          setState(RecordingState.ERROR)
          onTranscript('')
        })
    }, 0)
  }

  /**
   * 调用后端 Whisper API
   */
  const recognizeAudio = async (audioBlob) => {
    try {
      const result = await transcribeAudio(audioBlob)
      return result.data?.text || result.text || ''
    } catch (error) {
      // 如果后端Whisper不可用，返回模拟结果（用于演示）
      console.warn('Whisper API 不可用，使用模拟结果')
      return '【模拟语音输入】请配置 Whisper 服务'
    }
  }

  /**
   * 获取录音时长
   */
  const getDuration = () => {
    if (durationTimer) {
      return Math.floor(performance.now() / 1000)
    }
    return 0
  }

  /**
   * 重置状态
   */
  const reset = () => {
    stopRecording()
    audioChunks = []
    setState(RecordingState.IDLE)
  }

  /**
   * 获取当前状态
   */
  const getState = () => state

  /**
   * 检查是否支持录音
   */
  const isSupported = () => {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  }

  return {
    startRecording,
    stopRecording,
    reset,
    getState,
    getDuration,
    isSupported,
    RecordingState
  }
}

/**
 * 格式化时长
 * @param {number} seconds - 秒数
 */
export function formatDuration(seconds) {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}
