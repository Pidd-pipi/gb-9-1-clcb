import api from './axios'

export const orderApi = {
  list: (params?: { page?: number; size?: number }) =>
    api.get('/my/orders', { params }),

  requestInvoice: (orderId: string, data: any) =>
    api.post(`/orders/${orderId}/invoice`, data),
}
