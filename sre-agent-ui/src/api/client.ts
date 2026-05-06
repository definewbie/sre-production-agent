/**
 * SRE Agent API Client
 * 第一版使用 mock data，后续逐区接入真实 API
 * Mock data 已明确标记
 */

const API_BASE = 'http://localhost:8080'

interface ApiResponse<T> {
  data: T | null
  error: string | null
  isMock: boolean
}

async function request<T>(path: string, options?: RequestInit): Promise<ApiResponse<T>> {
  try {
    const res = await fetch(API_BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const data = await res.json()
    return { data, error: null, isMock: false }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    return { data: null, error: msg, isMock: true }
  }
}

export const api = {
  // Observability
  getObservabilityStatus: () => request('/api/observability/status'),

  // Demo Services
  getDemoServicesStatus: () => request('/api/demo-services/status'),

  // Live Scenario
  runLiveScenario: (config: Record<string, unknown>) =>
    request('/api/live-scenario/run', {
      method: 'POST',
      body: JSON.stringify(config),
    }),

  getLatestScenario: () => request('/api/live-scenario/latest'),

  resetScenario: () =>
    request('/api/live-scenario/reset', { method: 'POST' }),
}
