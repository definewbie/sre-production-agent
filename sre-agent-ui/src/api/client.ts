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

/* ── RCA Analysis View Model (V.2-UI-4) ── */

export type RcaDecisionType =
  | 'likely_root_cause'
  | 'probable_root_cause'
  | 'competing_hypotheses'
  | 'uncertain_requires_more_evidence'
  | 'insufficient_evidence'
  | 'unknown'

export interface HypothesisView {
  hypothesisId: string
  name: string
  rootCauseType?: string
  score: number
  level?: string
  decision?: string
  supportingCount: number
  counterCount: number
  missingCount: number
  contradictionCount: number
  explanation: string
  rank: number
}

export interface IncidentContext {
  service: string
  namespace: string
  severity: string
  alertName: string
  description: string
  startedAt?: string
  labels?: Record<string, string>
}

export interface RcaAnalysisView {
  source: 'real' | 'mock' | 'mixed'
  runId?: string
  scenarioName?: string
  status?: string
  phase?: string
  incident?: IncidentContext
  decisionType: RcaDecisionType
  selectedHypothesisId?: string
  confidenceScore: number
  scoreGap: number
  isCompeting: boolean
  competitionExplanation?: string
  nextProbes?: string[]
  evidenceWindow: {
    waitSeconds: number
    lookbackSeconds: number
    stepSeconds: number
    start?: string
    end?: string
    estimated?: boolean
  }
  hypotheses: HypothesisView[]
  keyExplanations: string[]
  evidenceSummary: {
    totalCount: number
    sources: Record<string, { count: number; available: boolean }>
  }
  durationMs?: number
  aiProposal?: {
    title?: string
    status?: string
    canAffectDecision?: boolean
    reasoning?: string
    analysisMarkdown?: string
    supportingSignals?: string[]
  }
  errorMessage?: string
}

/** Hypothesis ID → 中文名 */
const HYPOTHESIS_NAMES: Record<string, string> = {
  hyp_deployment_regression: '最近部署引入回归',
  hyp_downstream_dependency_latency: '下游依赖延迟导致超时',
  hyp_pod_oom_killed: 'Pod OOM 或资源压力',
  hyp_pod_crash_loop: '容器崩溃循环',
  deployment_regression: '最近部署引入回归',
  downstream_dependency_latency: '下游依赖延迟导致超时',
  pod_oom_killed: 'Pod OOM 或资源压力',
  pod_crash_loop: '容器崩溃循环',
  resource_saturation: '资源饱和',
}

/** Decision type → 中文 */
const DECISION_NAMES: Record<string, RcaDecisionType> = {
  likely_root_cause: 'likely_root_cause',
  probable_root_cause: 'probable_root_cause',
  competing_hypotheses: 'competing_hypotheses',
  uncertain_requires_more_evidence: 'uncertain_requires_more_evidence',
  insufficient_evidence: 'insufficient_evidence',
  unknown: 'unknown',
}

export function getDecisionLabel(dt: RcaDecisionType): string {
  switch (dt) {
    case 'likely_root_cause': return '已锁定高置信根因'
    case 'probable_root_cause': return '大概率是这个原因'
    case 'competing_hypotheses': return '几个原因都有可能，还没法确定'
    case 'uncertain_requires_more_evidence': return '线索不够，还得继续查'
    case 'insufficient_evidence': return '证据不足'
    case 'unknown': return '未知'
  }
}

/** 后端 decision_type 字段映射为前端 RcaDecisionType */
function normalizeDecisionType(raw?: string): RcaDecisionType {
  if (!raw) return 'unknown'
  const lower = raw.toLowerCase().replace(/-/g, '_')
  return DECISION_NAMES[lower] || 'unknown'
}

/** 兼容 camelCase / snake_case 取值 */
function flexGet(obj: Record<string, unknown>, camelKey: string, snakeKey: string): unknown {
  return obj[camelKey] ?? obj[snakeKey] ?? undefined
}

/** 将后端 LiveScenarioResult 转为前端 RcaAnalysisView */
function mapLiveScenarioToRcaView(raw: Record<string, unknown>): RcaAnalysisView {
  const baseRca = (raw.baseRca || raw.base_rca) as Record<string, unknown> | undefined
  const decision = baseRca ? (baseRca.decision || baseRca.decision) as Record<string, unknown> | undefined : undefined
  const comparison = baseRca ? (baseRca.comparison || baseRca.comparison) as Record<string, unknown> | undefined : undefined

  // Decision fields
  const rawDecisionType = decision ? String(flexGet(decision as Record<string, unknown>, 'decisionType', 'decision_type') || '') : ''
  const decisionType = normalizeDecisionType(rawDecisionType)
  const selectedHypothesisId = decision ? String(flexGet(decision as Record<string, unknown>, 'selectedHypothesisId', 'selected_hypothesis_id') || '') : undefined
  const confidenceScore = decision ? Number(flexGet(decision as Record<string, unknown>, 'confidenceScore', 'confidence_score') ?? 0) : 0
  const scoreGap = comparison ? Number(flexGet(comparison as Record<string, unknown>, 'scoreGap', 'score_gap') ?? 0) : 0

  // Incident context
  const rawIncident = baseRca ? (baseRca.incident || baseRca.incident) as Record<string, unknown> | undefined : undefined
  let incident: RcaAnalysisView['incident'] = undefined
  if (rawIncident) {
    incident = {
      service: String(rawIncident.service || ''),
      namespace: String(rawIncident.namespace || ''),
      severity: String(rawIncident.severity || ''),
      alertName: String(rawIncident.alert_name || rawIncident.alertName || ''),
      description: String((rawIncident.annotations as Record<string, unknown>)?.description || rawIncident.description || ''),
      startedAt: String(rawIncident.started_at || rawIncident.startedAt || ''),
      labels: (rawIncident.labels || {}) as Record<string, string>,
    }
  }

  // Next probes from decision
  const nextProbes: string[] = (decision?.next_probes || decision?.nextProbes || []) as string[]

  // Hypotheses
  const rawHypotheses = (baseRca?.hypotheses || []) as Record<string, unknown>[]
  const confidenceResults = (baseRca?.confidenceResults || baseRca?.confidence_results || []) as Record<string, unknown>[]
  const verificationResults = (baseRca?.verificationResults || baseRca?.verification_results || []) as Record<string, unknown>[]

  const confMap = new Map<string, Record<string, unknown>>()
  for (const cr of confidenceResults) {
    const hid = String(flexGet(cr, 'hypothesisId', 'hypothesis_id') || '')
    if (hid) confMap.set(hid, cr)
  }
  const verMap = new Map<string, Record<string, unknown>>()
  for (const vr of verificationResults) {
    const hid = String(flexGet(vr, 'hypothesisId', 'hypothesis_id') || '')
    if (hid) verMap.set(hid, vr)
  }

  const hypotheses: HypothesisView[] = rawHypotheses
    .map((h, idx) => {
      const hid = String(h.id || flexGet(h, 'hypothesisId', 'hypothesis_id') || '')
      const conf = confMap.get(hid)
      const ver = verMap.get(hid)
      const score = conf ? Number(conf.score ?? 0) : 0
      return {
        hypothesisId: hid,
        name: HYPOTHESIS_NAMES[hid] || HYPOTHESIS_NAMES[String(h.pattern_id || '')] || String(h.title || hid) + '（未知假设类型）',
        rootCauseType: String(flexGet(h, 'rootCauseType', 'root_cause_type') || ''),
        score,
        level: conf ? String(conf.level || '') : '',
        decision: conf ? String(conf.decision || '') : '',
        supportingCount: ver ? ((ver.supporting_evidence_ids || ver.supportingEvidenceIds) as unknown[])?.length || 0 : 0,
        counterCount: ver ? ((ver.counter_evidence_ids || ver.counterEvidenceIds) as unknown[])?.length || 0 : 0,
        missingCount: ver ? ((ver.missing_evidence || ver.missingEvidence) as unknown[])?.length || 0 : 0,
        contradictionCount: ver ? ((ver.contradictions || []) as unknown[])?.length || 0 : 0,
        explanation: ver ? String(ver.explanation || '') : '',
        rank: idx + 1,
      }
    })
    .sort((a, b) => b.score - a.score)
    .map((h, idx) => ({ ...h, rank: idx + 1 }))

  // Competing check
  const isCompeting =
    decisionType === 'competing_hypotheses' ||
    scoreGap === 0 ||
    (hypotheses.length >= 2 && hypotheses[0].score === hypotheses[1].score) ||
    (scoreGap > 0 && scoreGap < 0.10)

  let competitionExplanation: string | undefined
  if (isCompeting && hypotheses.length >= 2) {
    const top1 = hypotheses[0]
    const top2 = hypotheses[1]
    if (top1.score === top2.score) {
      competitionExplanation = `${top1.name} 与 ${top2.name} 得分相同（${top1.score.toFixed(2)}）。当前需要更多证据或后续探测区分两者。`
    } else {
      competitionExplanation = `${top1.name}（${top1.score.toFixed(2)}）与 ${top2.name}（${top2.score.toFixed(2)}）得分接近。系统不会仅因排序顺序将某一个假设判定为唯一根因。`
    }
  }

  // Key explanations from verification results
  const keyExplanations: string[] = []
  for (const vr of verificationResults) {
    const expl = String(vr.explanation || '')
    if (expl) keyExplanations.push(expl)
  }

  // Evidence summary
  const evidenceReport = (raw.evidenceReport || raw.evidence_report) as Record<string, unknown> | undefined
  const evidenceSummary: RcaAnalysisView['evidenceSummary'] = {
    totalCount: evidenceReport ? Number(evidenceReport.totalEvidenceCount || evidenceReport.total_evidence_count || 0) : 0,
    sources: {},
  }
  if (evidenceReport?.sources) {
    const srcMap = evidenceReport.sources as Record<string, Record<string, unknown>>
    for (const [name, report] of Object.entries(srcMap)) {
      evidenceSummary.sources[name] = {
        count: Number(report.evidenceCount || report.evidence_count || 0),
        available: Boolean(report.available),
      }
    }
  }

  // Evidence window
  const waitSeconds = Number(raw.waitSeconds || raw.wait_seconds || 30)
  const lookbackSeconds = Number(raw.lookbackSeconds || raw.lookback_seconds || 300)
  const stepSeconds = Number(raw.stepSeconds || raw.step_seconds || 15)
  const evStart = raw.evidenceWindowStart || raw.evidence_window_start
  const evEnd = raw.evidenceWindowEnd || raw.evidence_window_end

  // AI Proposal
  const llmProposal = (raw.llmProposal || raw.llm_proposal) as Record<string, unknown> | undefined
  let aiProposal: RcaAnalysisView['aiProposal'] = undefined
  if (llmProposal) {
    const proposals = (llmProposal.proposals || []) as Record<string, unknown>[]
    if (proposals.length > 0) {
      const first = proposals[0]
      aiProposal = {
        title: String(first.title || ''),
        status: String(first.status || '未验证'),
        canAffectDecision: Boolean(first.canAffectDecision ?? false),
        reasoning: String(first.reasoning || ''),
        analysisMarkdown: String(first.candidateCause || first.candidate_cause || ''),
        supportingSignals: (first.supportingSignals || first.supporting_signals || []) as string[],
      }
    }
  }

  return {
    source: 'real',
    runId: String(raw.scenarioId || raw.scenario_id || ''),
    scenarioName: String(raw.scenarioName || raw.scenario_name || ''),
    status: String(raw.status || ''),
    phase: String(raw.phase || ''),
    incident,
    decisionType,
    selectedHypothesisId,
    confidenceScore,
    scoreGap,
    isCompeting,
    competitionExplanation,
    nextProbes,
    evidenceWindow: {
      waitSeconds,
      lookbackSeconds,
      stepSeconds,
      start: evStart ? String(evStart) : undefined,
      end: evEnd ? String(evEnd) : undefined,
      estimated: !evStart,
    },
    hypotheses,
    keyExplanations,
    evidenceSummary,
    durationMs: Number(raw.durationMs || raw.duration_ms || 0),
    aiProposal,
    errorMessage: raw.errorMessage ? String(raw.errorMessage) : undefined,
  }
}

/** GET /api/live-scenario/latest */
export async function getLatestLiveScenario(): Promise<{ data: RcaAnalysisView | null; error: string | null }> {
  const result = await request<Record<string, unknown>>('/api/live-scenario/latest')
  if (result.error) return { data: null, error: result.error }
  if (!result.data) return { data: null, error: null }
  return { data: mapLiveScenarioToRcaView(result.data), error: null }
}

/** POST /api/live-scenario/run */
export async function runLiveScenarioForRca(payload?: {
  mode?: string
  faultMode?: string
  waitSeconds?: number
  lookbackSeconds?: number
  stepSeconds?: number
  runLlmProposal?: boolean
}): Promise<{ data: RcaAnalysisView | null; error: string | null }> {
  const body = {
    mode: payload?.mode || 'live',
    faultMode: payload?.faultMode || 'latency',
    waitSeconds: payload?.waitSeconds ?? 30,
    lookbackSeconds: payload?.lookbackSeconds ?? 300,
    stepSeconds: payload?.stepSeconds ?? 15,
    runLlmProposal: payload?.runLlmProposal ?? true,
  }
  const result = await request<Record<string, unknown>>('/api/live-scenario/run', {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (result.error) return { data: null, error: result.error }
  if (!result.data) return { data: null, error: '运行结果为空' }
  return { data: mapLiveScenarioToRcaView(result.data), error: null }
}

/** GET /api/live-scenario/simulate (quick fixture-only run) */
export async function simulateLiveScenario(runLlm?: boolean): Promise<{ data: RcaAnalysisView | null; error: string | null }> {
  const result = await request<Record<string, unknown>>('/api/live-scenario/simulate' + (runLlm ? '?runLlm=true' : ''))
  if (result.error) return { data: null, error: result.error }
  if (!result.data) return { data: null, error: '模拟结果为空' }
  return { data: mapLiveScenarioToRcaView(result.data), error: null }
}

/* ── Service Health View Model (V.2-UI-3) ── */

export type ServiceHealthStatus = 'healthy' | 'degraded' | 'down' | 'unknown'

/** 单服务 Prometheus 指标视图（来自 GET /api/metrics/services） */
export interface ServiceMetricsView {
  errorRate: string
  errorRateTrend: string
  errorRateDirection: 'up' | 'down'
  p95Latency: string
  p95Trend: string
  p95Direction: 'up' | 'down'
  rps: number
  saturation: number
  restarts: number
}

export interface ServicesMetricsResponse {
  source: 'real' | 'unavailable'
  /** key = service name，如 "order-service" */
  services: Record<string, ServiceMetricsView>
  checkedAt: string
}

export interface ServiceHealthView {
  name: string
  status: ServiceHealthStatus
  reachable: boolean
  url: string
  health: string
  /** 以下指标暂时来自 mock，字段本身可空 */
  errorRate?: string
  errorRateTrend?: string
  errorRateDirection?: 'up' | 'down'
  p95Latency?: string
  p95Trend?: string
  p95Direction?: 'up' | 'down'
  rps?: number
  saturation?: number
  restarts?: number
  faultEnabled: boolean
  faultType: string
  message: string
  source: 'real' | 'mock' | 'mixed'
}

export interface ServiceHealthSummary {
  checkedAt: string
  source: 'real' | 'mock' | 'mixed'
  totalServices: number
  healthyServices: number
  degradedServices: number
  downServices: number
  alerts: number
  affectedUsers: number
  affectedUsersSource: 'mock'
  alertsSource: 'mock'
  services: ServiceHealthView[]
  topology: Array<{ from: string; to: string; status: ServiceHealthStatus }>
  topologySource: 'real' | 'mock'
}

/** 将 faultConfig 字符串映射为展示用类型 */
function parseFaultType(fc: string): { enabled: boolean; type: string } {
  if (!fc || fc === 'normal' || fc === 'unknown') return { enabled: false, type: 'normal' }
  return { enabled: true, type: fc }
}

/** 将 demo service 映射为 ServiceHealthView */
function mapDemoService(s: DemoServiceStatus): ServiceHealthView {
  const fault = parseFaultType(s.faultConfig)
  let status: ServiceHealthStatus = 'unknown'
  if (!s.reachable) {
    status = 'down'
  } else if (fault.enabled) {
    status = 'degraded'
  } else {
    status = 'healthy'
  }
  return {
    name: s.service,
    status,
    reachable: s.reachable,
    url: s.url,
    health: s.health,
    faultEnabled: fault.enabled,
    faultType: fault.type,
    message: s.reachable ? (fault.enabled ? 'fault: ' + fault.type : s.health) : 'unreachable',
    // 暂无真实指标来源
    source: 'real',
  }
}

/** 解析 topology 字符串 "order-service → payment-service → inventory-service" */
function parseTopology(topoStr: string, services: ServiceHealthView[]): ServiceHealthSummary['topology'] {
  const parts = topoStr.split(/\s*→\s*/)
  if (parts.length < 2) return []
  const edges: ServiceHealthSummary['topology'] = []
  for (let i = 0; i < parts.length - 1; i++) {
    const fromSvc = services.find(s => s.name === parts[i].trim())
    const toSvc = services.find(s => s.name === parts[i + 1].trim())
    const status: ServiceHealthStatus = (fromSvc?.status === 'down' || toSvc?.status === 'down')
      ? 'down'
      : (fromSvc?.status === 'degraded' || toSvc?.status === 'degraded')
        ? 'degraded'
        : 'healthy'
    edges.push({ from: parts[i].trim(), to: parts[i + 1].trim(), status })
  }
  return edges
}

/**
 * getServicesMetrics()
 * 
 * 从 GET /api/metrics/services 获取 Prometheus 实时指标。
 * 返回 null 表示 Prometheus 不可用，调用方应降级到 mock。
 */
async function getServicesMetrics(): Promise<ServicesMetricsResponse | null> {
  try {
    const res = await fetch('/api/metrics/services')
    if (!res.ok) return null
    const json = await res.json()
    if (json.source === 'unavailable' || !json.services) return null
    return json as ServicesMetricsResponse
  } catch {
    return null
  }
}

/**
 * getServiceHealthSummary()
 * 
 * 从 /api/demo-services/status 获取真实服务列表和可达性。
 * 指标从 Prometheus 实时获取（GET /api/metrics/services），不可用时降级到 mock。
 * 告警和影响用户数暂用 mock。
 * 每个字段都标记来源（real / mock / mixed）。
 */
export async function getServiceHealthSummary(): Promise<{ data: ServiceHealthSummary | null; error: string | null }> {
  const demo = await getDemoServicesStatus()

  if (!demo.data) {
    return { data: null, error: demo.error || '服务健康数据获取失败' }
  }

  const rawServices = demo.data.services.map(mapDemoService)

  // 尝试从 Prometheus 获取真实指标
  const realMetrics = await getServicesMetrics()

  let metricsSource: 'real' | 'mock' | 'mixed' = 'mixed'
  let checkedAt: string

  if (realMetrics) {
    // Prometheus 可用 → 使用真实指标
    metricsSource = 'real'
    checkedAt = realMetrics.checkedAt
  } else {
    // Prometheus 不可用 → 降级为 mock 指标
    metricsSource = 'mixed'
    checkedAt = new Date().toISOString()
  }

  // Mock 降级默认值
  const mockDefaults: Record<string, Partial<ServiceHealthView>> = {
    'order-service': { errorRate: '4.7%', errorRateTrend: '350%', errorRateDirection: 'up', p95Latency: '1.85s', p95Trend: '280%', p95Direction: 'up', rps: 2.1, saturation: 45, restarts: 0 },
    'payment-service': { errorRate: '0.2%', errorRateTrend: '20%', errorRateDirection: 'up', p95Latency: '2.42s', p95Trend: '480%', p95Direction: 'up', rps: 2.0, saturation: 38, restarts: 0 },
    'inventory-service': { errorRate: '0.1%', errorRateTrend: '50%', errorRateDirection: 'down', p95Latency: '0.38s', p95Trend: '10%', p95Direction: 'down', rps: 1.8, saturation: 32, restarts: 0 },
  }

  const services = rawServices.map(svc => {
    if (realMetrics && realMetrics.services[svc.name]) {
      // 真实指标 + 真实可达性 → source = real
      const m = realMetrics.services[svc.name]
      return {
        ...svc,
        errorRate: m.errorRate,
        errorRateTrend: m.errorRateTrend,
        errorRateDirection: m.errorRateDirection,
        p95Latency: m.p95Latency,
        p95Trend: m.p95Trend,
        p95Direction: m.p95Direction,
        rps: m.rps,
        saturation: m.saturation,
        restarts: m.restarts,
        source: 'real' as const,
      }
    }
    // 降级到 mock
    const mock = mockDefaults[svc.name]
    if (!mock) return svc
    return {
      ...svc,
      ...mock,
      source: 'mixed' as const,
    }
  })

  const healthyCount = services.filter(s => s.status === 'healthy').length
  const degradedCount = services.filter(s => s.status === 'degraded').length
  const downCount = services.filter(s => s.status === 'down').length

  const topology = parseTopology(demo.data.topology, services)

  return {
    data: {
      checkedAt,
      source: metricsSource,
      totalServices: services.length,
      healthyServices: healthyCount,
      degradedServices: degradedCount,
      downServices: downCount,
      alerts: 2,
      affectedUsers: 128,
      affectedUsersSource: 'mock',
      alertsSource: 'mock',
      services,
      topology,
      topologySource: 'real',
    },
    error: null,
  }
}

/* ── Evidence Drill-down View Model (V.2-UI-5) ── */

export type EvidenceStrength = 'strong' | 'moderate' | 'weak' | 'unknown'

export type EvidenceSource = 'prometheus' | 'loki' | 'tracing' | 'kubernetes' | 'alertmanager' | 'unknown'

export interface EvidenceItemView {
  id: string
  source: EvidenceSource
  evidenceType: string
  content: string
  strength: EvidenceStrength
  service?: string
  entity?: string
  timestamp?: string
  attributes?: Record<string, unknown>
  isNoSignal: boolean
  isMetadata: boolean
  isEffective: boolean
}

export type SourceStatus = 'available' | 'empty' | 'unavailable' | 'unknown'

export interface SourceSummaryView {
  source: EvidenceSource
  displayName: string
  status: SourceStatus
  totalEvidence: number
  effectiveEvidence: number
  noSignalEvidence: number
  topSignals: string[]
  message?: string
  error?: string
}

export interface EvidenceDrilldownView {
  source: 'real' | 'mock' | 'mixed'
  runId?: string
  status?: string
  collectedAt?: string
  totalEvidence: number
  effectiveEvidence: number
  sourceSummaries: SourceSummaryView[]
  topEvidence: EvidenceItemView[]
  rawEvidence: EvidenceItemView[]
}

const NO_SIGNAL_TYPES = new Set([
  'none', 'k8s_no_signal', 'metric_no_signal', 'log_no_signal',
  'trace_no_signal', 'alert_no_signal', 'no_signal',
])

const METADATA_TYPES = new Set([
  'deployment_metadata', 'pod_metadata', 'replicaset_metadata',
  'k8s_workload_metadata', 'metadata',
])

const EFFECTIVE_TYPES = new Set([
  'metric_latency_p95_spike', 'metric_error_rate_spike', 'metric_restart_rate_increased',
  'metric_downstream_latency_spike', 'metric_memory_usage_high', 'metric_cpu_usage_high',
  'log_downstream_timeout', 'log_timeout_error', 'log_exception', 'log_exception_spike', 'log_http_5xx',
  'trace_downstream_span_slow', 'trace_child_span_dominates_latency', 'trace_error_span',
  'trace_root_span_slow', 'trace_timeout_span',
  'container_crash_loop_backoff', 'pod_restart_count_increased', 'pod_not_ready', 'container_oom_killed',
  'alert_firing', 'alert_severity_high',
])

function normalizeEvidenceSource(raw: string): EvidenceSource {
  const lower = (raw || '').toLowerCase()
  if (lower === 'jaeger' || lower === 'tracing') return 'tracing'
  if (lower === 'prometheus') return 'prometheus'
  if (lower === 'loki') return 'loki'
  if (lower === 'kubernetes' || lower === 'k8s') return 'kubernetes'
  if (lower === 'alertmanager') return 'alertmanager'
  return 'unknown'
}

const SOURCE_DISPLAY: Record<EvidenceSource, string> = {
  prometheus: 'Prometheus',
  loki: 'Loki',
  tracing: 'Jaeger / Tracing',
  kubernetes: 'Kubernetes',
  alertmanager: 'Alertmanager',
  unknown: '\u672a\u77e5',
}

function normalizeStrength(raw: unknown): EvidenceStrength {
  if (typeof raw === 'string') {
    const s = raw.toLowerCase()
    if (s === 'strong') return 'strong'
    if (s === 'moderate') return 'moderate'
    if (s === 'weak') return 'weak'
  }
  if (typeof raw === 'number') {
    if (raw >= 0.6) return 'strong'
    if (raw >= 0.3) return 'moderate'
    if (raw > 0) return 'weak'
    return 'unknown'
  }
  return 'unknown'
}

function mapEvidenceItem(raw: Record<string, unknown>): EvidenceItemView {
  const evidenceType = String(raw.evidenceType ?? raw.evidence_type ?? '')
  const source = normalizeEvidenceSource(String(raw.source ?? ''))
  const isNoSignal = NO_SIGNAL_TYPES.has(evidenceType) || evidenceType.endsWith('_no_signal')
  const isMetadata = METADATA_TYPES.has(evidenceType)
  const isEffective = EFFECTIVE_TYPES.has(evidenceType)

  return {
    id: String(raw.id || ''),
    source,
    evidenceType,
    content: String(raw.content || ''),
    strength: normalizeStrength(raw.strength ?? raw.strong),
    service: raw.service ? String(raw.service) : undefined,
    entity: raw.entity ? String(raw.entity) : undefined,
    timestamp: raw.timestamp ? String(raw.timestamp) : undefined,
    attributes: (raw.attributes || {}) as Record<string, unknown>,
    isNoSignal,
    isMetadata,
    isEffective,
  }
}

export function mapEvidenceDrilldownView(raw: Record<string, unknown>): EvidenceDrilldownView {
  const er = (raw.evidenceReport || raw.evidence_report) as Record<string, unknown> | undefined
  const allRaw = (er?.allEvidence || er?.all_evidence || []) as Record<string, unknown>[]
  const rawEvidence = allRaw.map(mapEvidenceItem)

  const srcReports = (er?.sources || {}) as Record<string, Record<string, unknown>>
  const knownSources: EvidenceSource[] = ['prometheus', 'loki', 'tracing', 'kubernetes', 'alertmanager']

  const sourceSummaries: SourceSummaryView[] = knownSources.map(src => {
    const report = Object.values(srcReports).find(r =>
      normalizeEvidenceSource(String(r.sourceName ?? '')) === src
    )

    const totalEvidence = report ? Number(report.evidenceCount ?? report.evidence_count ?? 0) : 0
    const available = report ? Boolean(report.available) : false
    const error = report?.error ? String(report.error) : undefined
    const evidenceTypes = (report?.evidenceTypes || report?.evidence_types || []) as string[]

    const srcItems = rawEvidence.filter(e => e.source === src)
    const effectiveCount = srcItems.filter(e => e.isEffective).length
    const noSignalCount = srcItems.filter(e => e.isNoSignal).length

    let status: SourceStatus = 'unknown'
    if (error) {
      status = 'unavailable'
    } else if (available && totalEvidence > 0) {
      status = 'available'
    } else if (available && totalEvidence === 0) {
      status = 'empty'
    }

    let message: string | undefined
    if (src === 'kubernetes' && available && totalEvidence === 0) {
      message = '\u8be5\u6765\u6e90\u53ef\u7528\uff0c\u4f46\u672c\u6b21\u672a\u53d1\u73b0 Pod / Runtime \u5f02\u5e38\u4fe1\u53f7\u3002'
    }

    return {
      source: src,
      displayName: SOURCE_DISPLAY[src],
      status,
      totalEvidence,
      effectiveEvidence: effectiveCount,
      noSignalEvidence: noSignalCount,
      topSignals: evidenceTypes.slice(0, 5),
      message,
      error,
    }
  })

  const effective = rawEvidence
    .filter(e => e.isEffective && !e.isNoSignal)
    .sort((a, b) => {
      const so = (s: EvidenceStrength) => s === 'strong' ? 0 : s === 'moderate' ? 1 : 2
      return so(a.strength) - so(b.strength)
    })

  const topEvidence: EvidenceItemView[] = []
  const srcCount: Record<string, number> = {}
  for (const e of effective) {
    if (topEvidence.length >= 10) break
    const cnt = srcCount[e.source] || 0
    if (cnt >= 4) continue
    srcCount[e.source] = cnt + 1
    topEvidence.push(e)
  }

  const effectiveTotal = rawEvidence.filter(e => e.isEffective).length

  return {
    source: 'real',
    runId: String(raw.scenarioId ?? raw.scenario_id ?? ''),
    status: String(raw.status || ''),
    collectedAt: raw.evidenceWindowEnd ? String(raw.evidenceWindowEnd) : undefined,
    totalEvidence: rawEvidence.length,
    effectiveEvidence: effectiveTotal,
    sourceSummaries,
    topEvidence,
    rawEvidence,
  }
}

export async function getEvidenceDrilldownView(): Promise<{ data: EvidenceDrilldownView | null; error: string | null }> {
  const result = await request<Record<string, unknown>>('/api/live-scenario/latest')
  if (result.error) return { data: null, error: result.error }
  if (!result.data) return { data: null, error: null }
  const view = mapEvidenceDrilldownView(result.data)
  if (view.totalEvidence === 0 && !view.runId) {
    return { data: null, error: null }
  }
  return { data: view, error: null }
}

/** Get evidence drilldown for a specific RCA Run by scenarioId. */
export async function getEvidenceDrilldownForRun(runId: string): Promise<{ data: EvidenceDrilldownView | null; error: string | null }> {
  const result = await request<Record<string, unknown>>('/api/live-scenario/' + encodeURIComponent(runId))
  if (result.error) return { data: null, error: result.error }
  if (!result.data) return { data: null, error: null }
  const view = mapEvidenceDrilldownView(result.data)
  if (view.totalEvidence === 0 && !view.runId) {
    return { data: null, error: null }
  }
  return { data: view, error: null }
}

/* ── RCA Run Status (V.2-UI-6.2) ── */

export type RcaRunStatus = 'NOT_STARTED' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'NO_EVIDENCE_FOUND' | 'FAILED'

/** Map backend status strings to RcaRunStatus */
export function normalizeRcaRunStatus(raw?: string): RcaRunStatus {
  if (!raw) return 'NOT_STARTED'
  const s = raw.toUpperCase().replace(/-/g, '_')
  if (s === 'COMPLETED') return 'COMPLETED'
  if (s === 'NO_EVIDENCE_FOUND') return 'NO_EVIDENCE_FOUND'
  if (s === 'RUNNING' || s === 'IN_PROGRESS') return 'RUNNING'
  if (s === 'QUEUED' || s === 'PENDING') return 'QUEUED'
  if (s === 'FAILED' || s === 'ERROR') return 'FAILED'
  return 'NOT_STARTED'
}

/* ── Incident / Alert-driven API (V.2-UI-6) ── */

export type AlertRelevance = 'SERVICE_ALERT' | 'PLATFORM_ALERT' | 'WATCHDOG_ALERT' | 'UNSUPPORTED_ALERT' | 'IGNORED_ALERT'

export interface AlertView {
  fingerprint: string
  alertName: string
  service: string
  namespace: string
  severity: string
  status: string
  startedAt: string
  summary: string
  labels: Record<string, string>
  /** V.2-UI-6.1: relevance classification */
  relevance: AlertRelevance
  /** Whether this alert can trigger RCA */
  rcaEligible: boolean
  /** If not RCA-eligible, the reason */
  ineligibleReason?: string
  classifiedAt?: string
  source?: string
}

export interface AlertSummary {
  totalAlerts: number
  serviceAlerts: number
  platformAlerts: number
  watchdogAlerts: number
  unsupportedAlerts: number
  ignoredAlerts: number
  rcaEligibleAlerts: number
}

export interface AlertsResponse {
  alerts: AlertView[]
  summary: AlertSummary
  source: string
  checkedAt: string
}

export interface IncidentRcaResultView {
  incidentId: string
  alertName: string
  service: string
  severity: string
  status: string  // "running" | "completed" | "failed" | "not_started" | "queued"
  decisionType?: string
  confidenceScore?: number
  topHypothesisName?: string
  hypothesisCount?: number
  durationMs?: number
  errorMessage?: string
  /** V.2-UI-6.2: additional fields for list display */
  namespace?: string
  startedAt?: string
  evidenceCount?: number
  triggerSource?: string  // "alert" | "manual" | "lab-demo"
  /** Alert fingerprint for rcaEligible checks */
  alertFingerprint?: string
  /** Whether this alert is eligible for RCA trigger */
  rcaEligible?: boolean
  ineligibleReason?: string
}

/** Fetch classified firing alerts with summary (V.2-UI-6.1). */
export async function getFiringAlerts(): Promise<{ data: AlertsResponse | null; error: string | null }> {
  return request<AlertsResponse>('/api/incidents/alerts')
}

/** List all incidents (alert-driven RCA results). */
export async function getIncidents(): Promise<{ data: IncidentRcaResultView[] | null; error: string | null }> {
  return request<IncidentRcaResultView[]>('/api/incidents')
}

/** Get a single incident result. */
export async function getIncident(incidentId: string): Promise<{ data: IncidentRcaResultView | null; error: string | null }> {
  return request<IncidentRcaResultView>('/api/incidents/' + encodeURIComponent(incidentId))
}

/** Trigger RCA from a specific alert. */
export async function triggerIncidentRca(payload: {
  fingerprint?: string
  alertName?: string
  service?: string
}): Promise<{ data: IncidentRcaResultView | null; error: string | null }> {
  return request<IncidentRcaResultView>('/api/incidents/from-alert', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** Get the full RCA analysis view for an incident (LiveScenarioResult-compatible). */
export async function getIncidentRcaAnalysis(incidentId: string): Promise<{ data: RcaAnalysisView | null; error: string | null }> {
  const result = await request<Record<string, unknown>>('/api/incidents/' + encodeURIComponent(incidentId) + '/rca')
  if (result.error) return { data: null, error: result.error }
  if (!result.data) return { data: null, error: null }
  return { data: mapLiveScenarioToRcaView(result.data), error: null }
}

/* ── Chaos Experiment API (V.2-UI-7) ── */

export type ChaosFaultType = 'latency' | 'error' | 'timeout' | 'resource_pressure'
export type ChaosStatus = 'IDLE' | 'CONFIGURED' | 'RUNNING' | 'STOPPING' | 'STOPPED' | 'FAILED' | 'UNKNOWN'

export interface ChaosConfig {
  targetService: string
  faultType: ChaosFaultType
  latencyMs?: number
  errorRate?: number
  durationSeconds?: number
  rps?: number
  experimentName?: string
  description?: string
}

export interface ChaosExperimentInfo {
  targetService: string
  faultType: string
  experimentName: string
  description: string
  active: boolean
  startedAt: string
  expectedEndAt: string
  stoppedAt?: string
  durationSeconds: number
  remainingSeconds: number
}

export interface ChaosServiceStatus {
  service: string
  health: string
  faultConfig: string
  reachable: boolean
  experiment?: ChaosExperimentInfo
}

export interface ChaosStatusResponse {
  services: ChaosServiceStatus[]
  topology: string
  activeExperiments: ChaosExperimentInfo[]
}

export interface ChaosActionResult {
  status: string
  targetService?: string
  faultType?: string
  startedAt?: string
  expectedEndAt?: string
  remainingSeconds?: number
  message?: string
  experiment?: ChaosExperimentInfo
  hint?: string
  error?: string
}

/** GET /api/chaos/status */
export async function getChaosStatus(): Promise<{ data: ChaosStatusResponse | null; error: string | null }> {
  return request<ChaosStatusResponse>('/api/chaos/status')
}

/** POST /api/chaos/start */
export async function startChaosExperiment(config: ChaosConfig): Promise<{ data: ChaosActionResult | null; error: string | null }> {
  return request<ChaosActionResult>('/api/chaos/start', {
    method: 'POST',
    body: JSON.stringify(config),
  })
}

/** POST /api/chaos/stop */
export async function stopChaosExperiment(targetService: string): Promise<{ data: ChaosActionResult | null; error: string | null }> {
  return request<ChaosActionResult>('/api/chaos/stop', {
    method: 'POST',
    body: JSON.stringify({ targetService }),
  })
}

/** POST /api/chaos/reset */
export async function resetChaosFaults(): Promise<{ data: ChaosActionResult | null; error: string | null }> {
  return request<ChaosActionResult>('/api/chaos/reset', {
    method: 'POST',
  })
}
