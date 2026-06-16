import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('API请求错误:', error)
    return Promise.reject(error)
  }
)

export const getStatus = () => {
  return request.get('/v1/schedule/status')
}

export const emergencyLimit = (maxTotalPowerKw, reason, transitionMinutes = 0) => {
  return request.post('/v1/schedule/emergency', {
    maxTotalPowerKw,
    reason,
    transitionMinutes
  })
}

export const resetSchedule = () => {
  return request.post('/v1/schedule/reset')
}

export default {
  getStatus,
  emergencyLimit,
  resetSchedule
}
