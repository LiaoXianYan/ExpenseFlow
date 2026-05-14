import request from './request'

export function reviewExpense(data: any) {
  return request.post('/ai/review/evaluate', data)
}
export function analyzeRisk(data: any) {
  return request.post('/ai/review/risk', data)
}
export function askRag(question: string) {
  return request.post('/ai/rag/ask', { question })
}
export function triggerOcrRecognition(invoiceId: number, imageUrl?: string) {
  return request.post('/ai/ocr/recognize', { invoiceId, imageUrl })
}
