/**
 * 与后端 common-core 中的 Result / PageResult 保持一致。
 * 后续接口返回类型在此基础上扩展，不在骨架阶段定义业务模型。
 */
export const RESULT_SUCCESS_CODE = 0

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}
