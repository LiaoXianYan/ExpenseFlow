import request from './request'

export function uploadInvoice(formData: FormData) {
  return request.post('/expense/invoice/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function getInvoiceList(params: any) {
  return request.get('/expense/invoice/page', { params })
}
export function triggerOcr(invoiceId: number) {
  return request.post(`/expense/invoice/${invoiceId}/ocr`)
}
export function getInvoiceDetail(id: number) {
  return request.get(`/expense/invoice/${id}`)
}
