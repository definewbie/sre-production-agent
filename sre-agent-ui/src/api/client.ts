/**
 * SRE Agent API Client
 * 
 * V.2-UI-2: 环境状态接真实 API
 * - 使用 Vite proxy（dev: /api → localhost:8080）
 * - build 产物部署在 Spring Boot static/，同源直接请求
 * - 失败不伪装成成功，显式返回 error
 */

/* ── 后端 DTO 类型 ── */

export interface ObservabilityEndpointStatus {
  name: string
  type: string
  url: string
  status: string        // "connected" | "disconnected" | "unknown" | "not_configured"
  latencyMs: number
  message: string
}

export interface ObservabilityStatusResponse {
  overallStatus: string  // "healthy" | "partial" | "down" | "unknown"
  checkedAt: string      // ISO instant
  endpoints: ObservabilityEndpointStatus[]
}

export interface DemoServiceStatus {
  service: string
  url: string
  health: string
  faultConfig: string    // "normal" | "latency" | "error" | "timeout" | "unknown"
  reachable: boolean
}

export interface DemoServicesStatusResponse {
  services: DemoServiceStatus[]
  topology: string
}

/* ── 前端 View Model ── */

export type ComponentStatus = 'healthy' | 'degraded' | 'down' | 'unknown'

export interface EnvironmentComponent {
  name: string
  category: 'observability' | 'runtime' | 'application' | 'api'
  status: ComponentStatus
  endpoint: string
  responseTimeMs: number
  lastCheckedAt: string
  message: string
  error?: string
}

export interface EnvironmentSummary {
  components: EnvironmentComponent[]
  total: number
  healthyCount: number
  degradedCount: number
  downCount: number
  unknownCount: number
  overallStatus: ComponentStatus
  checkedAt: string
  isMock: boolean
}

/* ── API 请求 ── */

const API_BASE = ''  // Vite proxy 或同源

async function request<T>(path: string, options?: RequestInit): Promise<{ data: T; error: null } | { data: null; error: string }> {
  try {
    const res = await fetch(API_BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error('HTTP ' + res.status + (text ? ': ' + text.slice(0, 200) : ''))
    }
    const data = await res.json()
    return { data, error: null }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    return { data: null, error: msg }
  }
}

/* ── 状态映射 ── */

function mapObservabilityStatus(raw: string): ComponentStatus {
  switch (raw) {
    case 'connected': return 'healthy'
    case 'disconnected': return 'down'
    case 'not_configured': return 'unknown'
    default: return 'unknown'
  }
}

function mapOverallStatus(raw: string): ComponentStatus {
  switch (raw) {
    case 'healthy': return 'healthy'
    case 'partial': return 'degraded'
    case 'down': return 'down'
    default: return 'unknown'
  }
}

function demoServiceStatus(s: DemoServiceStatus): ComponentStatus {
  if (!s.reachable) return 'down'
  if (s.faultConfig !== 'normal') return 'degraded'
  return 'healthy'
}

/* ── Public API ── */

export async function getObservabilityStatus(): Promise<{ data: ObservabilityStatusResponse | null; error: string | null }> {
  return request<ObservabilityStatusResponse>('/api/observability/status')
}

export async function getDemoServicesStatus(): Promise<{ data: DemoServicesStatusResponse | null; error: string | null }> {
  return request<DemoServicesStatusResponse>('/api/demo-services/status')
}

export async function triggerObservabilityCheck(): Promise<{ data: ObservabilityStatusResponse | null; error: string | null }> {
  return request<ObservabilityStatusResponse>('/api/observability/status/check', { method: 'POST' })
}

/**
 * 合并两个 API 返回为统一 EnvironmentSummary
 * - SRE Agent API 状态由前端请求成功本身推断
 * - 如果两个 API 都失败，返回 error
 * - 如果部分成功，合并成功部分 + 标记失败部分
 */
export async function getEnvironmentSummary(): Promise<{ data: EnvironmentSummary | null; error: string | null }> {
  const [obs, demo] = await Promise.all([
    getObservabilityStatus(),
    getDemoServicesStatus(),
  ])

  const components: EnvironmentComponent[] = []
  let hasRealData = false
  const errors: string[] = []
  let checkedAt = new Date().toISOString()

  // 1. Observability endpoints
  if (obs.data) {
    hasRealData = true
    checkedAt = obs.data.checkedAt || checkedAt
    for (const ep of obs.data.endpoints) {
      components.push({
        name: ep.name,
        category: 'observability',
        status: mapObservabilityStatus(ep.status),
        endpoint: ep.url,
        responseTimeMs: ep.latencyMs,
        lastCheckedAt: obs.data.checkedAt,
        message: ep.message || '',
      })
    }
  } else {
    errors.push('Observability: ' + (obs.error || '未知错误'))
  }

  // 2. Demo services → grouped as one component
  if (demo.data) {
    hasRealData = true
    const allReachable = demo.data.services.every(s => s.reachable)
    const hasFault = demo.data.services.some(s => s.faultConfig !== 'normal')
    const avgMs = 0 // demo API 不返回延迟，用 0
    components.push({
      name: 'Demo Services',
      category: 'application',
      status: allReachable ? (hasFault ? 'degraded' : 'healthy') : 'down',
      endpoint: demo.data.topology,
      responseTimeMs: avgMs,
      lastCheckedAt: new Date().toISOString(),
      message: demo.data.services.map(s => s.service + ': ' + s.faultConfig).join(', '),
    })
  } else {
    errors.push('Demo Services: ' + (demo.error || '未知错误'))
  }

  // 3. SRE Agent API — 请求成功即 healthy
  if (obs.data || demo.data) {
    components.push({
      name: 'SRE Agent API',
      category: 'api',
      status: 'healthy',
      endpoint: '/api',
      responseTimeMs: 0,
      lastCheckedAt: new Date().toISOString(),
      message: 'API 响应正常',
    })
  }

  // 4. Kubernetes — 从 observability endpoints 中查找
  //    后端 ObservabilityStatusService 会检查 kubernetes type
  const k8sFromObs = obs.data?.endpoints?.find(e => e.type === 'kubernetes')
  if (k8sFromObs) {
    // 已在上面推入 components，不再重复
  } else if (hasRealData) {
    // 后端没返回 Kubernetes 单独端点，标记 unknown
    components.push({
      name: 'Kubernetes',
      category: 'runtime',
      status: 'unknown',
      endpoint: '-',
      responseTimeMs: 0,
      lastCheckedAt: new Date().toISOString(),
      message: '后端未返回 Kubernetes 状态',
    })
  }

  if (!hasRealData) {
    return { data: null, error: errors.join('; ') }
  }

  const healthyCount = components.filter(c => c.status === 'healthy').length
  const degradedCount = components.filter(c => c.status === 'degraded').length
  const downCount = components.filter(c => c.status === 'down').length
  const unknownCount = components.filter(c => c.status === 'unknown').length

  const overallStatus: ComponentStatus =
    downCount > 0 ? 'down' :
    degradedCount > 0 ? 'degraded' :
    unknownCount > 0 ? 'degraded' :
    'healthy'

  return {
    data: {
      components,
      total: components.length,
      healthyCount,
      degradedCount,
      downCount,
      unknownCount,
      overallStatus,
      checkedAt,
      isMock: false,
    },
    error: errors.length > 0 ? errors.join('; ') : null,
  }
}

/**
 * TopBar 轻量摘要：只检查关键组件是否 OK
 */
export interface TopBarEnvStatus {
  prometheus: boolean
  loki: boolean
  jaeger: boolean
  demoServices: boolean
  api: boolean
  allOk: boolean
  isMock: boolean
  error: string | null
}

export async function getTopBarEnvStatus(): Promise<TopBarEnvStatus> {
  const result = await getEnvironmentSummary()
  
  if (!result.data) {
    return {
      prometheus: false,
      loki: false,
      jaeger: false,
      demoServices: false,
      api: false,
      allOk: false,
      isMock: false,
      error: result.error,
    }
  }

  const { components } = result.data
  const find = (name: string) => components.find(c => c.name === name)
  const ok = (c?: { status: ComponentStatus }) => c?.status === 'healthy'

  const prometheus = ok(find('Prometheus'))
  const loki = ok(find('Loki'))
  const jaeger = ok(find('Jaeger'))
  const demoServices = ok(find('Demo Services'))
  const api = ok(find('SRE Agent API'))
  const allOk = prometheus && loki && jaeger && demoServices && api

  return { prometheus, loki, jaeger, demoServices, api, allOk, isMock: false, error: null }
}
