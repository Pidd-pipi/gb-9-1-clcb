import api from './axios'

export const audioApi = {
  list: (params?: { page?: number; size?: number }) =>
    api.get('/audio', { params }),

  mine: () => api.get('/audio/mine'),

  getById: (id: string) => api.get(`/audio/${id}`),

  getEpisodes: (courseId: string) => api.get(`/audio/${courseId}/episodes`),

  getEpisode: (courseId: string, episodeId: string) =>
    api.get(`/audio/${courseId}/episodes/${episodeId}`),

  getAccess: (courseId: string, episodeId: string) =>
    api.get(`/audio/${courseId}/episodes/${episodeId}/access`),

  // <audio> 元素无法携带 Authorization 头，登录用户通过查询参数附带 token
  getStreamUrl: (courseId: string, episodeId: string) => {
    const base = `/api/audio/${courseId}/episodes/${episodeId}/stream`
    const token = localStorage.getItem('token')
    return token ? `${base}?token=${encodeURIComponent(token)}` : base
  },

  purchase: (id: string) => api.post(`/audio/${id}/purchase`),

  getPurchaseStatus: (id: string) => api.get(`/audio/${id}/purchase-status`),

  getProgress: (courseId: string, episodeId: string) =>
    api.get(`/audio/${courseId}/episodes/${episodeId}/progress`),

  saveProgress: (courseId: string, episodeId: string, position: number) =>
    api.post(`/audio/${courseId}/episodes/${episodeId}/progress`, {
      position: Math.max(0, Math.floor(position)),
    }),

  getCourseProgress: (courseId: string) => api.get(`/audio/${courseId}/progress`),

  offline: (id: string) => api.post(`/audio/${id}/offline`),

  publish: (id: string) => api.post(`/audio/${id}/publish`),

  create: (data: any) => api.post('/audio', data),

  createEpisode: (courseId: string, data: any) =>
    api.post(`/audio/${courseId}/episodes`, data),
}
