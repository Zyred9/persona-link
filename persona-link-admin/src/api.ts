interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export async function getHealth(): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/health`)
  if (!response.ok) {
    throw new Error(`服务端响应异常：${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<string>
  if (body.code !== 0) {
    throw new Error(body.message || '服务端业务响应异常')
  }
  return body.data
}
