import { useState, useEffect, useCallback, useRef } from 'react'
import {
  getServiceHealthSummary,
  getFiringAlerts,
  getIncidents,
  triggerIncidentRca,
  ServiceHealthSummary,
  ServiceHealthView,
  ServiceHealthStatus,
  type AlertView,
  type AlertsResponse,
  type AlertSummary,
  type IncidentRcaResultView,
} from '../api/client'
import { ChevronDown, RefreshCw, Zap } from 'lucide-react'

interface Props {
  onServiceClick: (name: string) => void
  onRcaTriggered?: (incidentId: string) => void
}

// Mini sparkline SVG component
function Sparkline({ color, width = 60, height = 20 }: { color: string; width?: number; height?: number }) {
  const pts = [0.6, 0.35, 0.55, 0.25, 0.5, 0.3]
  const stepX = width / (pts.length - 1)
  const pathD = pts.map((p, i) => {
    const x = i * stepX
    const y = p * height
    return (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1)
  }).join(' ')

  return (
    <svg width={width} height={height} style={{ verticalAlign: 'middle', marginLeft: 4 }}>
      <path d={pathD} fill="none" stroke={color} strokeWidth="2" />
    </svg>
  )
}

const statusBadgeClass: Record<ServiceHealthStatus, string> = {
  healthy: 'badge badge-green',
  degraded: 'badge badge-orange',
  down: 'badge badge-red',
  unknown: 'badge',
}

const statusLabel: Record<ServiceHealthStatus, string> = {
  healthy: '正常',
  degraded: '降级',
  down: '不可用',
  unknown: '未知',
}

const topoEdgeColor: Record<ServiceHealthStatus, string> = {
  healthy: '#12b76a',
  degraded: '#f79009',
  down: '#f04438',
  unknown: '#98a2b3',
}

const topoNodeClass: Record<ServiceHealthStatus, string> = {
  healthy: 'green',
  degraded: 'orange',
  down: 'red',
  unknown: '',
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

export default function ServiceHealthOverview({ onServiceClick, onRcaTriggered }: Props) {
  const [summary, setSummary] = useState<ServiceHealthSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [alerts, setAlerts] = useState<AlertView[]>([])
  const [alertSummary, setAlertSummary] = useState<AlertSummary | null>(null)
  const [alertsLoading, setAlertsLoading] = useState(true)
  const [triggeringAlert, setTriggeringAlert] = useState<string | null>(null)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [timeRange, setTimeRange] = useState<'5m' | '15m' | '1h' | '6h'>('5m')
  const [timeDropdownOpen, setTimeDropdownOpen] = useState(false)
  const timeDropdownRef = useRef<HTMLDivElement>(null)
  // Mixed-source alerts: Alertmanager + chaos incidents
  const [incidents, setIncidents] = useState<IncidentRcaResultView[]>([])
  const [incidentsLoading, setIncidentsLoading] = useState(true)

  // Pure Alertmanager service alerts (for summary count display)
  const serviceAlerts = alerts.filter(a => a.relevance === 'SERVICE_ALERT')

  // Merge Alertmanager alerts with RCA incidents for unified display
  interface MixedAlertItem {
    fingerprint: string
    alertName: string
    service: string
    severity: string
    summary: string
    rcaEligible: boolean
    source: 'Alertmanager' | 'RCA'
  }

  // Convert incidents to alert-like items
  const incidentAlerts: MixedAlertItem[] = incidents.map(inc => ({
    fingerprint: inc.incidentId,
    alertName: inc.alertName || `RCA: ${inc.service}`,
    service: inc.service,
    severity: (inc.decisionType === 'ROOT_CAUSE_CONFIRMED' || (inc.confidenceScore != null && inc.confidenceScore > 0.7))
      ? 'critical' : 'warning',
    summary: inc.decisionType
      ? `决策: ${inc.decisionType}, 置信度: ${((inc.confidenceScore ?? 0) * 100).toFixed(0)}%`
      : inc.errorMessage || `状态: ${inc.status}`,
    rcaEligible: false,
    source: 'RCA' as const,
  }))
  const mixedAlerts: MixedAlertItem[] = [
    ...serviceAlerts.map(a => ({
      fingerprint: a.fingerprint || a.alertName,
      alertName: a.alertName,
      service: a.service,
      severity: a.severity,
      summary: a.summary || (a.service ? '服务: ' + a.service : ''),
      rcaEligible: a.rcaEligible,
      source: 'Alertmanager' as const,
    })),
    ...incidentAlerts,
  ]

  const loadData = useCallback(async () => {
    setRefreshing(true)
    setError(null)
    const result = await getServiceHealthSummary()
    if (result.data) {
      setSummary(result.data)
      setError(result.error)
    } else {
      setSummary(null)
      setError(result.error || '服务健康数据获取失败')
    }
    setRefreshing(false)
    setLoading(false)
  }, [])

  const loadAlerts = useCallback(async () => {
    setAlertsLoading(true)
    const result = await getFiringAlerts()
    if (result.data) {
      setAlerts(result.data.alerts)
      setAlertSummary(result.data.summary)
    } else {
      setAlerts([])
      setAlertSummary(null)
    }
    setAlertsLoading(false)
  }, [])

  const loadIncidents = useCallback(async () => {
    setIncidentsLoading(true)
    const result = await getIncidents()
    if (result.data) {
      // Filter to completed/significant incidents (chaos or manual, skip pure alert-derived?)
      setIncidents(result.data.filter(inc => inc.status === 'COMPLETED' || inc.status === 'RUNNING'))
    } else {
      setIncidents([])
    }
    setIncidentsLoading(false)
  }, [])

  useEffect(() => {
    loadData()
    loadAlerts()
    loadIncidents()
  }, [loadData, loadAlerts, loadIncidents])

  // Auto-refresh interval
  useEffect(() => {
    if (!autoRefresh) return
    const intervalMap = { '5m': 30_000, '15m': 60_000, '1h': 120_000, '6h': 300_000 }
    const interval = setInterval(() => {
      loadData()
      loadAlerts()
      loadIncidents()
    }, intervalMap[timeRange])
    return () => clearInterval(interval)
  }, [autoRefresh, timeRange, loadData, loadAlerts, loadIncidents])

  // Click outside to close time dropdown
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (timeDropdownRef.current && !timeDropdownRef.current.contains(e.target as Node)) {
        setTimeDropdownOpen(false)
      }
    }
    if (timeDropdownOpen) {
      document.addEventListener('mousedown', handleClick)
      return () => document.removeEventListener('mousedown', handleClick)
    }
  }, [timeDropdownOpen])

  const timeRangeLabels: Record<string, string> = {
    '5m': '最近 5 分钟',
    '15m': '最近 15 分钟',
    '1h': '最近 1 小时',
    '6h': '最近 6 小时',
  }

  // Loading
  if (loading) {
    return (
      <div>
        <h1 className="page-title">服务健康总览</h1>
        <div style={{ padding: 40, textAlign: 'center', color: 'var(--muted)' }}>
          正在加载服务健康状态...
        </div>
      </div>
    )
  }

  // Error + no data
  if (!summary) {
    return (
      <div>
        <h1 className="page-title">服务健康总览</h1>
        <div style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ color: 'var(--red)', marginBottom: 12, fontSize: 15 }}>
            服务健康状态获取失败
          </div>
          <div style={{ color: 'var(--muted)', fontSize: 13, marginBottom: 20 }}>
            {error}
          </div>
          <button className="btn btn-primary btn-sm" onClick={loadData}>
            重试
          </button>
        </div>
      </div>
    )
  }

  const svcs = summary.services
  const hasMixed = summary.source === 'mixed' || svcs.some(s => s.source === 'mixed')
  const hasMock = summary.source === 'mixed' || summary.source === 'mock'
  const mockSup = hasMock ? (<sup style={{ fontSize: 10, color: 'var(--orange)' }}>M</sup>) : null

  return (
    <div>
      {/* Page Title + Time Controls */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">1 服务健康总览</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div ref={timeDropdownRef} style={{ position: 'relative' }}>
            <button
              className="btn btn-ghost btn-sm"
              style={{ display: 'flex', alignItems: 'center', gap: 4 }}
              onClick={() => setTimeDropdownOpen(o => !o)}
            >
              {timeRangeLabels[timeRange]}
              <ChevronDown size={14} style={{ transform: timeDropdownOpen ? 'rotate(180deg)' : undefined, transition: 'transform 0.2s' }} />
            </button>
            {timeDropdownOpen && (
              <>
                <div
                  style={{
                    position: 'fixed',
                    inset: 0,
                    zIndex: 99,
                  }}
                  onClick={() => setTimeDropdownOpen(false)}
                />
                <div style={{
                  position: 'absolute',
                  top: '100%',
                  left: 0,
                  marginTop: 4,
                  background: 'var(--surface)',
                  border: '1px solid var(--line)',
                  borderRadius: 8,
                  boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
                  zIndex: 100,
                  minWidth: 160,
                  padding: '4px 0',
                }}>
                {(['5m', '15m', '1h', '6h'] as const).map(opt => (
                  <div
                    key={opt}
                    onClick={(e) => { e.stopPropagation(); setTimeRange(opt); setTimeDropdownOpen(false) }}
                    style={{
                      padding: '8px 16px',
                      cursor: 'pointer',
                      fontSize: 13,
                      color: timeRange === opt ? 'var(--blue)' : 'var(--text)',
                      background: timeRange === opt ? 'var(--blue-bg)' : 'transparent',
                      fontWeight: timeRange === opt ? 600 : 400,
                      transition: 'background 0.15s',
                    }}
                    onMouseEnter={e => { if (timeRange !== opt) (e.target as HTMLElement).style.background = 'var(--hover-bg)' }}
                    onMouseLeave={e => { if (timeRange !== opt) (e.target as HTMLElement).style.background = 'transparent' }}
                  >
                    {timeRangeLabels[opt]}
                  </div>
                ))}
              </div>
              </>
            )}
          </div>
          <div
            style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--muted)', cursor: 'pointer', userSelect: 'none' }}
            onClick={() => setAutoRefresh(a => !a)}
          >
            自动刷新：
            <div className={'toggle' + (autoRefresh ? ' active' : '')} style={{ transform: 'scale(0.8)' }}>
              <div className="toggle-knob" />
            </div>
          </div>
          <span style={{ fontSize: 13, color: 'var(--muted)' }}>
            更新时间：{formatTime(summary.checkedAt)}
          </span>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => { loadData(); loadAlerts(); loadIncidents() }}
            disabled={refreshing}
            style={{ display: 'flex', alignItems: 'center', gap: 4 }}
          >
            <RefreshCw size={14} className={refreshing ? 'spin' : ''} />
            刷新
          </button>
        </div>
      </div>

      {/* Partial error warning */}
      {error && (
        <div style={{ background: '#fff3cd', border: '1px solid #ffc107', borderRadius: 8, padding: '10px 16px', marginBottom: 16, fontSize: 13, color: '#856404' }}>
          ⚠ {error}
        </div>
      )}

      {/* Data source indicator */}
      {summary.source === 'real' ? (
        <div style={{ background: '#e8f5e9', border: '1px solid #81c784', borderRadius: 8, padding: '10px 16px', marginBottom: 16, fontSize: 13, color: '#2e7d32' }}>
          ✅ 指标来自 Prometheus 实时数据（错误率、P95 延迟、RPS、饱和度、重启数）
        </div>
      ) : hasMixed && (
        <div style={{ background: '#f0f4ff', border: '1px solid #b2ccff', borderRadius: 8, padding: '10px 16px', marginBottom: 16, fontSize: 13, color: '#3366cc' }}>
          ⚠ Prometheus 不可用，部分指标降级为 Mock（错误率、P95 延迟、流量、饱和度、重启数），服务可达性和 fault 状态来自真实 API
        </div>
      )}

      {/* KPI Cards Row */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
        <div className="kpi-card">
          <div className="kpi-label">服务总数</div>
          <div className="kpi-value">{summary.totalServices}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">异常服务</div>
          <div className="kpi-value red">{summary.downServices + summary.degradedServices}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">健康服务</div>
          <div className="kpi-value green">{summary.healthyServices}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">
            活跃告警
            {alertsLoading && <span style={{ fontSize: 11, color: 'var(--muted)', marginLeft: 6, fontWeight: 400 }}>加载中...</span>}
          </div>
          <div className="kpi-value orange">{serviceAlerts.length}</div>
          {alertSummary && alertSummary.totalAlerts > serviceAlerts.length && (
            <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 2 }}>
              共 {alertSummary.totalAlerts} 条（已过滤 {alertSummary.totalAlerts - serviceAlerts.length} 条平台/基础设施告警）
            </div>
          )}
        </div>
        <div className="kpi-card" style={{ minWidth: 200 }}>
          <div className="kpi-label">
            影响用户
            <span style={{ fontSize: 11, color: 'var(--orange)', marginLeft: 6, fontWeight: 400 }}>Mock</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
            <div className="kpi-value">{summary.affectedUsers}</div>
            <span style={{ fontSize: 12, color: 'var(--red)', fontWeight: 600 }}>↑ 12%</span>
          </div>
        </div>
      </div>

      {/* Service Health Table */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-title">关键服务健康状态</div>
        <table className="data-table">
          <thead>
            <tr>
              <th>服务名称</th>
              <th>状态</th>
              <th>错误率 (5m){mockSup}</th>
              <th>P95 延迟 (5m){mockSup}</th>
              <th>流量 (rps){mockSup}</th>
              <th>饱和度{mockSup}</th>
              <th>最近重启{mockSup}</th>
            </tr>
          </thead>
          <tbody>
            {svcs.map(s => {
              const isAbnormal = s.status === 'down' || s.status === 'degraded'
              const valColor = isAbnormal ? 'red' : 'green'
              return (
                <tr key={s.name} style={{ cursor: 'pointer' }} onClick={() => onServiceClick(s.name)}>
                  <td style={{ fontWeight: 600 }}>{s.name}</td>
                  <td>
                    <span className={statusBadgeClass[s.status]}>
                      {statusLabel[s.status]}
                    </span>
                  </td>
                  <td>
                    {s.errorRate ? (
                      <>
                        <span className={valColor}>{s.errorRate}</span>
                        {s.errorRateTrend && (
                          <span className={'kpi-trend ' + (s.errorRateDirection || 'up')}>
                            {s.errorRateDirection === 'up' ? '↑' : '↓'}{s.errorRateTrend}
                          </span>
                        )}
                      </>
                    ) : (
                      <span style={{ color: 'var(--muted)' }}>-</span>
                    )}
                  </td>
                  <td>
                    {s.p95Latency ? (
                      <>
                        <span className={valColor}>{s.p95Latency}</span>
                        {s.p95Trend && (
                          <span className={'kpi-trend ' + (s.p95Direction || 'up')}>
                            {s.p95Direction === 'up' ? '↑' : '↓'}{s.p95Trend}
                          </span>
                        )}
                      </>
                    ) : (
                      <span style={{ color: 'var(--muted)' }}>-</span>
                    )}
                  </td>
                  <td>
                    {s.rps !== undefined ? (
                      <>
                        {s.rps}
                        <Sparkline color={isAbnormal ? '#2e90fa' : '#12b76a'} />
                      </>
                    ) : (
                      <span style={{ color: 'var(--muted)' }}>-</span>
                    )}
                  </td>
                  <td>
                    {s.saturation !== undefined ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span>{s.saturation}%</span>
                        <div className="saturation-bar">
                          <div
                            className="saturation-fill"
                            style={{
                              width: s.saturation + '%',
                              background: s.saturation > 70 ? 'var(--red)' : 'var(--green)',
                            }}
                          />
                        </div>
                      </div>
                    ) : (
                      <span style={{ color: 'var(--muted)' }}>-</span>
                    )}
                  </td>
                  <td>{s.restarts !== undefined ? s.restarts : '-'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Bottom Row: Topology + Alerts */}
      <div style={{ display: 'flex', gap: 20 }}>
        {/* Service Dependency Topology */}
        <div className="card" style={{ flex: '1.5' }}>
          <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            服务依赖拓扑（当前影响路径）
            {summary.topologySource === 'real' && (
              <span style={{ fontSize: 11, color: 'var(--green)', fontWeight: 400 }}>Live</span>
            )}
          </div>
          <div style={{ padding: '24px 16px' }}>
            {(() => {
              const serviceByName = new Map(svcs.map(svc => [svc.name, svc]))
              const incoming = new Set(summary.topology.map(edge => edge.to))
              const rootName = svcs.find(svc => !incoming.has(svc.name))?.name || svcs[0]?.name
              const rootSvc = rootName ? serviceByName.get(rootName) : undefined
              const downstreamEdges = summary.topology.filter(edge => edge.from === rootName)
              const downstreamSvcs = downstreamEdges
                .map(edge => serviceByName.get(edge.to))
                .filter((svc): svc is ServiceHealthView => Boolean(svc))

              const renderNode = (svc: ServiceHealthView) => {
                const isAbnormal = svc.status === 'down' || svc.status === 'degraded'
                const detailText = isAbnormal
                  ? (svc.faultEnabled ? 'fault: ' + svc.faultType : (svc.errorRate || 'unreachable'))
                  : (svc.p95Latency || '正常')
                return (
                  <div
                    className={'topo-node ' + topoNodeClass[svc.status]}
                    style={{ cursor: 'pointer', minWidth: 150 }}
                    onClick={() => onServiceClick(svc.name)}
                  >
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{svc.name}</div>
                    <div style={{ marginTop: 6 }}>
                      <span className={statusBadgeClass[svc.status]}>{statusLabel[svc.status]}</span>
                    </div>
                    <div style={{ fontSize: 12, color: isAbnormal ? 'var(--red)' : 'var(--green)', marginTop: 4 }}>
                      {detailText}
                    </div>
                  </div>
                )
              }

              if (rootSvc && downstreamSvcs.length > 1) {
                return (
                  <div style={{ display: 'grid', gridTemplateColumns: 'minmax(180px, 260px) 120px minmax(220px, 1fr)', alignItems: 'center', columnGap: 20 }}>
                    <div style={{ display: 'flex', justifyContent: 'center' }}>
                      {renderNode(rootSvc)}
                    </div>
                    <svg width="120" height="170" viewBox="0 0 120 170" aria-hidden="true">
                      {downstreamEdges.map((edge, index) => {
                        const targetY = downstreamEdges.length === 2 ? (index === 0 ? 45 : 125) : 85
                        const edgeColor = topoEdgeColor[edge.status]
                        return (
                          <g key={edge.from + edge.to}>
                            <path
                              d={`M 0 85 C 45 85, 55 ${targetY}, 104 ${targetY}`}
                              fill="none"
                              stroke={edgeColor}
                              strokeWidth="2"
                            />
                            <polygon
                              points={`104,${targetY - 5} 116,${targetY} 104,${targetY + 5}`}
                              fill={edgeColor}
                            />
                          </g>
                        )
                      })}
                    </svg>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, alignItems: 'center' }}>
                      {downstreamSvcs.map(renderNode)}
                    </div>
                  </div>
                )
              }

              return svcs.map((svc, i) => {
              const isAbnormal = svc.status === 'down' || svc.status === 'degraded'
              const detailText = isAbnormal
                ? (svc.faultEnabled ? 'fault: ' + svc.faultType : (svc.errorRate || 'unreachable'))
                : (svc.p95Latency || '正常')
              return (
                <span key={svc.name} style={{ display: 'inline-flex', alignItems: 'center', flexShrink: 0 }}>
                  {/* Node */}
                  <div
                    className={'topo-node ' + topoNodeClass[svc.status]}
                    style={{ cursor: 'pointer', minWidth: 150 }}
                    onClick={() => onServiceClick(svc.name)}
                  >
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{svc.name}</div>
                    <div style={{ marginTop: 6 }}>
                      <span className={statusBadgeClass[svc.status]}>{statusLabel[svc.status]}</span>
                    </div>
                    <div style={{ fontSize: 12, color: isAbnormal ? 'var(--red)' : 'var(--green)', marginTop: 4 }}>
                      {detailText}
                    </div>
                  </div>
                  {/* Arrow between nodes */}
                  {i < svcs.length - 1 && (() => {
                    const edge = summary.topology[i]
                    const edgeColor = edge ? topoEdgeColor[edge.status] : '#98a2b3'
                    return (
                      <div style={{ padding: '0 20px', flexShrink: 0 }}>
                        <svg width="80" height="20" viewBox="0 0 80 20">
                          <line x1="0" y1="10" x2="66" y2="10" stroke={edgeColor} strokeWidth="2" />
                          <polygon points="66,5 78,10 66,15" fill={edgeColor} />
                        </svg>
                      </div>
                    )
                  })()}
                </span>
              )
              })
            })()}
          </div>
        </div>

        {/* Recent Alerts — Real Alertmanager alerts (SERVICE_ALERT only, V.2-UI-6.1) */}
        <div className="card" style={{ flex: '1' }}>
          <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            业务告警
            <span style={{ fontSize: 11, color: 'var(--green)', fontWeight: 400 }}>Live</span>
            {alertSummary && alertSummary.totalAlerts > serviceAlerts.length && (
              <span style={{ fontSize: 11, color: 'var(--muted)', fontWeight: 400, marginLeft: 4 }}>
                (已过滤 {alertSummary.platformAlerts + alertSummary.watchdogAlerts + alertSummary.unsupportedAlerts} 条非业务告警)
              </span>
            )}
            <button
              className="btn btn-ghost btn-sm"
              style={{ marginLeft: 'auto', fontSize: 12 }}
              onClick={() => { loadAlerts(); loadIncidents() }}
              disabled={alertsLoading || incidentsLoading}
            >
              <RefreshCw size={12} className={alertsLoading || incidentsLoading ? 'spin' : ''} />
            </button>
          </div>
          <div>
            {(alertsLoading || incidentsLoading) && mixedAlerts.length === 0 && (
              <div style={{ padding: '24px 0', textAlign: 'center', color: 'var(--muted)', fontSize: 13 }}>
                正在加载告警和 RCA 事件...
              </div>
            )}
            {!alertsLoading && !incidentsLoading && mixedAlerts.length === 0 && (
              <div style={{ padding: '24px 0', textAlign: 'center', color: 'var(--muted)', fontSize: 13 }}>
                当前无业务告警（Alertmanager 无 firing alerts，无 RCA 事件记录）
              </div>
            )}
            {mixedAlerts.map((a, i) => (
              <div key={a.fingerprint || i} style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
                padding: '14px 0',
                borderTop: i > 0 ? '1px solid var(--line)' : 'none',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span className="status-dot" style={{
                    background: a.severity === 'critical' ? 'var(--red)' : 'var(--orange)',
                    flexShrink: 0,
                    width: 10,
                    height: 10,
                  }} />
                  <span style={{ flex: 1, fontSize: 14, color: 'var(--text)', fontWeight: 600 }}>
                    {a.alertName}
                  </span>
                  <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 4, background: a.source === 'RCA' ? 'rgba(139,92,246,0.15)' : 'rgba(59,130,246,0.15)', color: a.source === 'RCA' ? '#8b5cf6' : '#3b82f6' }}>
                    {a.source === 'RCA' ? '🤖 RCA' : '📡 Alertmanager'}
                  </span>
                  <span className={'badge ' + (a.severity === 'critical' ? 'badge-red' : 'badge-orange')}>
                    {a.severity === 'critical' ? '严重' : '警告'}
                  </span>
                </div>
                <div style={{ fontSize: 13, color: 'var(--muted)', paddingLeft: 20 }}>
                  {a.summary || (a.service ? '服务: ' + a.service : '')}
                </div>
                {a.rcaEligible && (
                  <div style={{ paddingLeft: 20 }}>
                    <button
                      className="btn btn-primary btn-sm"
                      style={{ fontSize: 12, padding: '4px 12px', display: 'flex', alignItems: 'center', gap: 4 }}
                      disabled={triggeringAlert !== null}
                      onClick={async () => {
                        setTriggeringAlert(a.fingerprint)
                        const result = await triggerIncidentRca({ fingerprint: a.fingerprint })
                        setTriggeringAlert(null)
                        if (result.data && result.data.incidentId) {
                          if (onRcaTriggered) onRcaTriggered(result.data.incidentId)
                        } else {
                          alert('RCA 触发失败: ' + (result.error || '未知错误'))
                        }
                      }}
                    >
                      <Zap size={12} />
                      {triggeringAlert === a.fingerprint ? '分析中...' : '触发 RCA 分析'}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="footer-note">
        * 服务状态（reachable / fault）来自真实 API · 指标（错误率 / 延迟 / RPS / 饱和度 / 重启）来自 Prometheus 实时数据 · 帮助邮箱 dev-platform@company.com
      </div>
    </div>
  )
}
