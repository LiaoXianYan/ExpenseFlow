import request from './request'

export function getTaskList(candidateGroup?: string) {
  return request.get('/approval/task/page', { params: { candidateGroup } })
}
export function completeTask(taskId: string, data: any) {
  return request.post(`/approval/task/${taskId}/complete`, data)
}
export function delegateTask(taskId: string, delegateToUser: string) {
  return request.post(`/approval/task/${taskId}/delegate?delegateToUser=${delegateToUser}`)
}
export function getApprovalRecords(businessType: string, businessId: number) {
  return request.get('/approval/task/record/list', { params: { businessType, businessId } })
}
