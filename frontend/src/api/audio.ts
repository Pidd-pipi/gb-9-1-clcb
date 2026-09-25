import api from './axios'

export const TRIAL_SECONDS = 60

export const audioApi = {
  list: (params?: { page?: number; size?: number }) =>
    api.get('/audio', { params }),

  getById: (id: string) => api.get(`/audio/${id}`),

  getEpisodes: (courseId: string) => api.get(`/audio/${courseId}/episodes`),

  getEpisode: (courseId: string, episodeId: string) =>
    api.get(`/audio/${courseId}/episodes/${episodeId}`),

  getStreamUrl: (courseId: string, episodeId: string) => {
    // <audio> 标签无法携带 Authorization 头，登录态通过 token 查询参数传递
    const token = localStorage.getItem('token')
    const base = `/api/audio/${courseId}/episodes/${episodeId}/stream`
    return token ? `${base}?token=${encodeURIComponent(token)}` : base
  },

  purchase: (id: string) => api.post(`/audio/${id}/purchase`),

  getProgress: (courseId: string, episodeId: string) =>
    api.get(`/audio/${courseId}/episodes/${episodeId}/progress`),

  saveProgress: (
    courseId: string,
    episodeId: string,
    data: { position: number; duration?: number; completed?: boolean }
  ) => api.post(`/audio/${courseId}/episodes/${episodeId}/progress`, data),

  continueEpisode: (courseId: string) =>
    api.get(`/audio/${courseId}/continue`),

  offline: (id: string) => api.post(`/audio/${id}/offline`),

  publish: (id: string) => api.post(`/audio/${id}/publish`),

  create: (data: any) => api.post('/audio', data),

  createEpisode: (courseId: string, data: any) =>
    api.post(`/audio/${courseId}/episodes`, data),

  uploadEpisode: (
    courseId: string,
    data: { file: File; title?: string; description?: string },
    onProgress?: (percent: number) => void
  ) => {
    const formData = new FormData()
    formData.append('file', data.file)
    if (data.title) formData.append('title', data.title)
    if (data.description) formData.append('description', data.description)
    return api.post(`/audio/${courseId}/episodes/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      },
    })
  },
}
