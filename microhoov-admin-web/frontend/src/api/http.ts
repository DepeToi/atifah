import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

import { RESULT_SUCCESS_CODE, type Result } from './types'

/**
 * 统一请求实例。
 *
 * 所有管理端接口统一走网关前缀 /admin/api，由网关转发到 admin-service（BFF）。
 * 登录、Token 注入与 401 跳转在独立鉴权接入后补充。
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/admin/api',
  timeout: 10_000,
})

http.interceptors.request.use((config) => {
  // TODO: 接入管理端独立鉴权后，在此附加 Authorization 请求头
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Result<unknown>>) => {
    if (error.response?.status === 401) {
      // TODO: 独立鉴权接入后清理登录态并跳转登录页
    }

    const message =
      error.response?.data?.message || error.message || '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/**
 * 发起请求并解包统一响应结构，业务代码直接拿到 data。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const { data } = await http.request<Result<T>>(config)

  if (data.code !== RESULT_SUCCESS_CODE) {
    ElMessage.error(data.message || '请求失败')
    throw new Error(data.message || '请求失败')
  }

  return data.data
}

export default http
