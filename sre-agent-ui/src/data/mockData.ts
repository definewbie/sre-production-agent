// Mock Data for SRE Agent Console v0.1
// 所有数据为模拟数据，明确标记

export const MOCK_TIMESTAMP = '2025-05-20 14:30:25'
export const MOCK_ENV = 'local-kind-demo'

export interface ServiceInfo {
  name: string
  status: 'normal' | 'abnormal'
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

export const services: ServiceInfo[] = [
  {
    name: 'order-service',
    status: 'abnormal',
    errorRate: '4.7%',
    errorRateTrend: '350%',
    errorRateDirection: 'up',
    p95Latency: '1.85s',
    p95Trend: '280%',
    p95Direction: 'up',
    rps: 2.1,
    saturation: 45,
    restarts: 0,
  },
  {
    name: 'payment-service',
    status: 'abnormal',
    errorRate: '0.2%',
    errorRateTrend: '20%',
    errorRateDirection: 'up',
    p95Latency: '2.42s',
    p95Trend: '480%',
    p95Direction: 'up',
    rps: 2.0,
    saturation: 38,
    restarts: 0,
  },
  {
    name: 'inventory-service',
    status: 'normal',
    errorRate: '0.1%',
    errorRateTrend: '50%',
    errorRateDirection: 'down',
    p95Latency: '0.38s',
    p95Trend: '10%',
    p95Direction: 'down',
    rps: 1.8,
    saturation: 32,
    restarts: 0,
  },
]

export const alerts = [
  { service: 'order-service', message: '高错误率', level: 'P1' as const, time: '14:25:10' },
  { service: 'payment-service', message: '高延迟', level: 'P2' as const, time: '14:24:58' },
]

export const incidentSummary = {
  service: 'order-service',
  detectedAt: '14:24:58',
  duration: '5 分钟',
  status: '异常持续中',
  summary: '错误率升高、checkout 延迟升高、存在 downstream timeout',
  impactChain: 'order-service → payment-service',
  details: [
    '错误率在 14:20 开始显著上升',
    'P95 延迟在 14:21 开始明显上升',
    '与 payment-service 调用延迟高度相关',
    '未发现 Pod 重启或 OOM 事件',
  ],
  endpoints: [
    { path: 'POST /checkout', errorRate: '6.2%', p95: '2.1s', requests: 120 },
    { path: 'GET /orders', errorRate: '3.1%', p95: '1.2s', requests: 85 },
    { path: 'POST /orders', errorRate: '2.8%', p95: '0.9s', requests: 65 },
  ],
}

export const rcaResult = {
  judgment: '竞争假设',
  judgmentDetail: '两个候选根因得分接近，证据不足以唯一收敛。',
  analysisTime: '20.9s',
  hypotheses: [
    {
      rank: 1,
      name: '下游依赖延迟导致超时',
      score: 1.0,
      confidence: '高置信',
      tags: ['证据最集中'],
      tagColors: ['blue', 'green'] as const,
    },
    {
      rank: 2,
      name: '最近部署引入回归',
      score: 1.0,
      confidence: '高置信',
      tags: ['同分需谨慎'],
      tagColors: ['orange'] as const,
    },
  ],
  explanations: [
    { text: 'payment-service P95 延迟显著升高（2.42s）', tag: '强支持', tagType: 'blue' as const, bg: 'highlight' as const },
    { text: 'order-service 出现 downstream timeout 错误', tag: '强支持', tagType: 'blue' as const, bg: 'highlight' as const },
    { text: 'Jaeger 显示 payment-service span 占主要耗时', tag: '强支持', tagType: 'blue' as const, bg: 'highlight' as const },
    { text: 'Kubernetes 未发现 order-service OOM / CrashLoop', tag: '反驳 OOM', tagType: 'green' as const, bg: 'green-bg' as const },
  ],
  evidenceOverview: {
    total: 229,
    sources: { Prometheus: 54, Loki: 62, Jaeger: 88, Kubernetes: 19, Alertmanager: 6 },
  },
  aiAdvisory: {
    title: '支付服务延迟可能被订单服务超时和重试策略放大',
    detail: '建议验证：order-service retry count、payment-service p95/p99 latency、timeout/retry policy 是否近期变化。',
    boundary: '边界：AI 只提出可验证假设，不修改当前 RCA 结论。',
  },
}

export const evidenceData = {
  sources: [
    { name: 'Prometheus', count: 54, noSignal: 1 },
    { name: 'Loki', count: 62, noSignal: 5 },
    { name: 'Jaeger', count: 88, noSignal: 0 },
    { name: 'Kubernetes', count: 19, noSignal: 0 },
    { name: 'Alertmanager', count: 0, noSignal: 6 },
  ],
  topEvidence: [
    { time: '14:29:15', source: 'Jaeger', type: 'downstream_span_slow', summary: 'payment-service span 耗时 2.3s，占 checkout 主要耗时', strength: 'strong' as const },
    { time: '14:29:12', source: 'Loki', type: 'timeout_error', summary: 'Read timed out calling POST http://payment-service', strength: 'strong' as const },
    { time: '14:28:58', source: 'Prometheus', type: 'latency_p95', summary: 'http_server_requests_seconds_bucket 显示 p95 latency spike', strength: 'strong' as const },
    { time: '14:28:45', source: 'Jaeger', type: 'child_span_dominates', summary: 'payment-service span 占 checkout 约 78%', strength: 'strong' as const },
    { time: '14:28:30', source: 'Loki', type: 'downstream_timeout', summary: 'Downstream request to payment-service timed out', strength: 'moderate' as const },
    { time: '14:28:20', source: 'Prometheus', type: 'metric_no_signal', summary: 'payment error_rate 未检测到异常，该来源未收集到有效异常信号', strength: 'none' as const },
  ],
  total: 235,
}

export const environmentComponents = [
  { name: 'Prometheus', status: 'normal', endpoint: 'http://localhost:9090', responseTime: '120ms', lastCheck: '14:30:25', action: '打开' },
  { name: 'Loki', status: 'normal', endpoint: 'http://localhost:3100', responseTime: '95ms', lastCheck: '14:30:25', action: '打开' },
  { name: 'Jaeger', status: 'normal', endpoint: 'http://localhost:16686', responseTime: '180ms', lastCheck: '14:30:25', action: '打开' },
  { name: 'Kubernetes', status: 'normal', endpoint: 'kind/sre-agent', responseTime: '60ms', lastCheck: '15 pods running', action: '查看' },
  { name: 'Demo Services', status: 'normal', endpoint: 'demo namespace', responseTime: '3/3 ready', lastCheck: '14:30:25', action: '查看' },
  { name: 'SRE Agent API', status: 'normal', endpoint: 'http://localhost:8080', responseTime: '45ms', lastCheck: '14:30:25', action: '打开' },
]

export const defaultSettings = {
  waitSeconds: 30,
  lookbackSeconds: 300,
  stepSeconds: 15,
  apiBaseUrl: 'http://localhost:8080',
  environmentName: 'local-kind-demo',
  refreshInterval: 30,
  autoRefresh: true,
  showRawEvidence: false,
  enableMockData: true,
  errorRateThreshold: 1.0,
  p95Threshold: 1000,
  rcaScoreDiff: 0.10,
}
