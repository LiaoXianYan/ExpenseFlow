import request from './request'

export function getPaymentPage(params: any) {
  return request.get('/expense/payment/page', { params })
}
export function executePayment(reportId: number) {
  return request.post(`/expense/payment/${reportId}/pay`)
}
