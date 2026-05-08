import { useState, useEffect, useCallback } from 'react'
import {
  getIncidents,
  getFiringAlerts,
  triggerIncidentRca,
  runLiveScenarioForRca,
  normalizeRcaRunStatus,
  type IncidentRcaResultView,
  type AlertView,
  type RcaAnalysisView,
} from '../api/client'

/* ── Constants ── */

type TabId = 'rca-runs' | 'alerts'

const STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '待分析',
  QUEUED: '排队中',
  RUNNING: '分析中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  NOT_STARTED: { bg: '#f2f4f7', text: '#667085' },
  QUEUED: { bg: '#fef0c7', text: '#b54708' },
  RUNNING: { bg: '#e0efff', text: '#175cd3' },
  COMPLETED: { bg: '#dcfae6', text: '#027a48' },
  FAILED: { bg: '#fee4e2', text: '#b42318' },
}

const DECISION_LABELS: Record<string, string> = {
  likely_root_cause: '已定位',
  probable_root_cause: '大概率',
  competing_hypotheses: '竞争假设',
  insufficient_evidence: '证据不足',
  uncertain_requires_more_evidence: '需更多证据',
  no_anomalous_evidence: '无异常证据',
  unknown: '-',
}

const SEVERITY_MAP: Record<string, { label: string; color: string }> = {
  critical: { label: '严重', color: '#f04438' },
  warning: { label: '警告', color: '#f79009' },
  info: { label: '信息', color: '#667085' },
}

const TRIGGER_SOURCE_LABELS: Record<string, string> = {
  alert: '告警',
  'lab-demo': 'Lab Demo',
  manual: '手动',
}

const TIME_RANGE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: '60', label: '最近1小时' },
  { value: '360', label: '最近6小时' },
  { value: '1440', label: '最近24小时' },
  { value: '10080', label: '最近7天' },
]

const SOURCE_OPTIONS = [
  { value: 'all', label: '全部来源' },
  { value: 'alert', label: 'Alert驱动' },
  { value: 'lab-demo', label: 'Lab Demo' },
  { value: 'manual', label: '手动触发' },
]

/* ── Filter State ── */

interface Filters {
  service: string
  status: string
  severity: string
  timeRange: string
  source: string
}

/* ── Props ── */

export interface RcaRunsListProps {
  onViewDetail: (incidentId: string, meta?: IncidentRcaResultView) => void
  onLabDemoResult: (result?: RcaAnalysisView) => void
}

/* ── Helpers ── */

function formatStartedAt(iso?: string): string {
  if (!iso) return '-'
  try {
    const d = new Date(iso)
    if (isNaN(d.getTime())) return '-'
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return '-'
  }
}

function isWithinMinutes(iso: string | undefined, minutes: number): boolean {
  if (!iso) return false
  try {
    const d = new Date(iso)
    if (isNaN(d.getTime())) return false
    const diffMs = Date.now() - d.getTime()
    return diffMs >= 0 && diffMs <= minutes * 60 * 1000
  } catch {
    return false
  }
}

/* ── Main Component ── */

export default function RcaRunsList({ onViewDetail, onLabDemoResult }: RcaRunsListProps) {
  const [activeTab, setActiveTab] = useState<TabId>('rca-runs')
  const [incidents, setIncidents] = useState<IncidentRcaResultView[]>([])
  const [alerts, setAlerts] = useState<AlertView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filters, setFilters] = useState<Filters>({
    service: 'all',
    status: 'all',
    severity: 'all',
    timeRange: 'all',
    source: 'all',
  })
  const [runningLabDemo, setRunningLabDemo] = useState(false)
  const [triggeringFp, setTriggeringFp] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (activeTab === 'rca-runs') {
        const result = await getIncidents()
        if (result.error) {
          setError(result.error)
          setIncidents([])
        } else {
          setIncidents(result.data || [])
        }
      } else {
        const result = await getFiringAlerts()
        if (result.error) {
          setError(result.error)
          setAlerts([])
        } else {
          // Only show RCA-eligible alerts
          setAlerts((result.data?.alerts || []).filter(a => a.rcaEligible))
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载数据失败')
    } finally {
      setLoading(false)
    }
  }, [activeTab])

  useEffect(() => { loadData() }, [loadData])

  /* ── Handlers ── */

  const handleLabDemo = async () => {
    setRunningLabDemo(true)
    try {
      const result = await runLiveScenarioForRca({
        mode: 'live',
        faultMode: 'latency',
        waitSeconds: 30,
        lookbackSeconds: 300,
        stepSeconds: 15,
        runLlmProposal: true,
      })
      if (result.error) {
        setError(result.error)
      } else {
        onLabDemoResult(result.data || undefined)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Lab Demo 运行失败')
    } finally {
      setRunningLabDemo(false)
    }
  }

  const handleTriggerRca = async (alert: AlertView) => {
    setTriggeringFp(alert.fingerprint)
    try {
      const result = await triggerIncidentRca({
        fingerprint: alert.fingerprint,
        alertName: alert.alertName,
        service: alert.service,
      })
      if (result.error) {
        setError(result.error)
      } else if (result.data) {
        onViewDetail(result.data.incidentId)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '触发 RCA 失败')
    } finally {
      setTriggeringFp(null)
    }
  }

  /* ── Derived Data ── */

  const filtered = incidents.filter(inc => {
    if (filters.service !== 'all' && inc.service !== filters.service) return false
    if (filters.status !== 'all' && normalizeRcaRunStatus(inc.status) !== filters.status) return false
    if (filters.severity !== 'all' && inc.severity !== filters.severity) return false
    // Time range filter
    if (filters.timeRange !== 'all') {
      const minutes = parseInt(filters.timeRange, 10)
      if (!isNaN(minutes) && !isWithinMinutes(inc.startedAt, minutes)) return false
    }
    // Source filter
    if (filters.source !== 'all' && inc.triggerSource !== filters.source) return false
    return true
  })

  const stats = {
    notStarted: filtered.filter(i => normalizeRcaRunStatus(i.status) === 'NOT_STARTED').length,
    running: filtered.filter(i => normalizeRcaRunStatus(i.status) === 'RUNNING').length,
    completed: filtered.filter(i => normalizeRcaRunStatus(i.status) === 'COMPLETED').length,
    insufficient: filtered.filter(i =>
      normalizeRcaRunStatus(i.status) === 'COMPLETED' &&
      (i.decisionType === 'insufficient_evidence' || i.decisionType === 'uncertain_requires_more_evidence')
    ).length,
    highConf: filtered.filter(i =>
      normalizeRcaRunStatus(i.status) === 'COMPLETED' &&
      (i.decisionType === 'likely_root_cause' || i.decisionType === 'probable_root_cause')
    ).length,
  }

  const services = [...new Set(incidents.map(i => i.service).filter(Boolean))].sort()

  /* ── Render ── */

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>RCA 分析</div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">RCA 分析</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button className="btn btn-ghost btn-sm" onClick={loadData} style={{ fontSize: 13 }}>
            ⟳ 刷新
          </button>
          <button
            className="btn btn-primary btn-sm"
            onClick={handleLabDemo}
            disabled={runningLabDemo}
            style={{ background: '#7a5af8', borderColor: '#7a5af8' }}
          >
            {runningLabDemo ? '运行中...' : '🔬 运行 Lab Demo RCA'}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div style={{
        display: 'flex', gap: 24,
        borderBottom: '2px solid var(--line)', marginBottom: 16,
      }}>
        {([
          { id: 'rca-runs' as TabId, label: 'RCA Runs' },
          { id: 'alerts' as TabId, label: '告警 / Incident' },
        ]).map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{
              background: 'none', border: 'none', padding: '8px 0',
              cursor: 'pointer', fontSize: 14,
              fontWeight: activeTab === tab.id ? 700 : 400,
              color: activeTab === tab.id ? 'var(--blue)' : 'var(--muted)',
              borderBottom: activeTab === tab.id ? '2px solid var(--blue)' : '2px solid transparent',
              marginBottom: -2,
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Error Banner */}
      {error && (
        <div className="alert-banner red" style={{ marginBottom: 16 }}>
          <span className="alert-title">{error}</span>
        </div>
      )}

      {/* ─── Tab: RCA Runs ─── */}
      {activeTab === 'rca-runs' && (
        <>
          {/* Summary Cards */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <SummaryCard label="待分析" value={stats.notStarted} color="#667085" />
            <SummaryCard label="分析中" value={stats.running} color="#175cd3" />
            <SummaryCard label="已完成" value={stats.completed} color="#027a48" />
            <SummaryCard label="证据不足" value={stats.insufficient} color="#f79009" />
            <SummaryCard label="高置信根因" value={stats.highConf} color="#039855" />
          </div>

          {/* Filters */}
          <div className="card" style={{ padding: '10px 16px', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
            <FilterSelect label="服务" value={filters.service}
              onChange={v => setFilters(f => ({ ...f, service: v }))}
              options={[
                { value: 'all', label: '全部服务' },
                ...services.map(s => ({ value: s, label: s })),
              ]}
            />
            <FilterSelect label="状态" value={filters.status}
              onChange={v => setFilters(f => ({ ...f, status: v }))}
              options={[
                { value: 'all', label: '全部状态' },
                { value: 'NOT_STARTED', label: '待分析' },
                { value: 'RUNNING', label: '分析中' },
                { value: 'COMPLETED', label: '已完成' },
                { value: 'FAILED', label: '失败' },
              ]}
            />
            <FilterSelect label="严重程度" value={filters.severity}
              onChange={v => setFilters(f => ({ ...f, severity: v }))}
              options={[
                { value: 'all', label: '全部' },
                { value: 'critical', label: '严重' },
                { value: 'warning', label: '警告' },
                { value: 'info', label: '信息' },
              ]}
            />
            <FilterSelect label="时间范围" value={filters.timeRange}
              onChange={v => setFilters(f => ({ ...f, timeRange: v }))}
              options={TIME_RANGE_OPTIONS}
            />
            <FilterSelect label="来源" value={filters.source}
              onChange={v => setFilters(f => ({ ...f, source: v }))}
              options={SOURCE_OPTIONS}
            />
            <span style={{ fontSize: 12, color: 'var(--muted)', marginLeft: 'auto' }}>
              共 {filtered.length} 条
            </span>
          </div>

          {/* Table / Loading / Empty */}
          {loading ? (
            <div style={{ padding: 60, textAlign: 'center', color: 'var(--muted)' }}>加载中...</div>
          ) : filtered.length === 0 ? (
            <div className="card" style={{ padding: 40, textAlign: 'center' }}>
              <div style={{ fontSize: 40, marginBottom: 16, opacity: 0.3 }}>📋</div>
              <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--title)', marginBottom: 8 }}>
                当前暂无 RCA 分析记录
              </div>
              <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 4 }}>
                请从「告警 / Incident」标签页触发 RCA，或手动运行 Lab Demo RCA
              </div>
              <div style={{ fontSize: 12, color: '#98a2b3', marginBottom: 16 }}>
                💡 Lab Demo 结果会直接跳转到分析详情页，不会出现在此列表中
              </div>
              <button
                className="btn btn-primary btn-sm"
                onClick={handleLabDemo}
                disabled={runningLabDemo}
                style={{ background: '#7a5af8', borderColor: '#7a5af8' }}
              >
                {runningLabDemo ? '运行中...' : '🔬 运行 Lab Demo RCA'}
              </button>
            </div>
          ) : (
            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: 140 }}>开始时间</th>
                    <th style={{ minWidth: 160 }}>Alert Name</th>
                    <th style={{ width: 120 }}>服务</th>
                    <th style={{ width: 80 }}>严重程度</th>
                    <th style={{ width: 80 }}>来源</th>
                    <th style={{ width: 90 }}>RCA 状态</th>
                    <th style={{ width: 100 }}>决策结果</th>
                    <th style={{ width: 80, textAlign: 'center' }}>置信度</th>
                    <th style={{ width: 60, textAlign: 'center' }}>证据数</th>
                    <th style={{ width: 100, textAlign: 'right' }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(inc => {
                    const status = normalizeRcaRunStatus(inc.status)
                    const statusLabel = STATUS_LABELS[status] || status
                    const statusColor = STATUS_COLORS[status] || STATUS_COLORS.NOT_STARTED
                    const decision = inc.decisionType
                      ? DECISION_LABELS[inc.decisionType.toLowerCase()] || '-'
                      : '-'
                    const sourceLabel = inc.triggerSource
                      ? TRIGGER_SOURCE_LABELS[inc.triggerSource] || inc.triggerSource
                      : '-'
                    return (
                      <tr key={inc.incidentId}>
                        <td style={{ fontFamily: 'monospace', fontSize: 11, whiteSpace: 'nowrap' }}>
                          {formatStartedAt(inc.startedAt)}
                        </td>
                        <td style={{ fontWeight: 600, fontSize: 13 }}>{inc.alertName || inc.incidentId}</td>
                        <td style={{ fontSize: 13 }}>{inc.service || '-'}</td>
                        <td><SeverityBadge severity={inc.severity} /></td>
                        <td style={{ fontSize: 12 }}>{sourceLabel}</td>
                        <td>
                          <Badge label={statusLabel} bg={statusColor.bg} text={statusColor.text} />
                        </td>
                        <td style={{ fontSize: 13 }}>{decision}</td>
                        <td style={{ textAlign: 'center', fontWeight: 600, fontSize: 13 }}>
                          {inc.confidenceScore != null ? (inc.confidenceScore * 100).toFixed(0) + '%' : '-'}
                        </td>
                        <td style={{ textAlign: 'center', fontSize: 13 }}>
                          {inc.evidenceCount != null ? inc.evidenceCount : '-'}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          {status === 'NOT_STARTED' && (
                            <button className="btn btn-primary btn-sm" style={{ fontSize: 11, padding: '2px 8px' }}
                              onClick={() => onViewDetail(inc.incidentId)}>
                              运行 RCA
                            </button>
                          )}
                          {(status === 'RUNNING' || status === 'QUEUED') && (
                            <button className="btn btn-ghost btn-sm" style={{ fontSize: 11 }}
                              onClick={() => onViewDetail(inc.incidentId)}>
                              查看
                            </button>
                          )}
                          {status === 'COMPLETED' && (
                            <button className="btn btn-ghost btn-sm" style={{ fontSize: 11 }}
                              onClick={() => onViewDetail(inc.incidentId)}>
                              查看
                            </button>
                          )}
                          {status === 'FAILED' && (
                            <button className="btn btn-ghost btn-sm" style={{ fontSize: 11, color: '#f04438' }}
                              onClick={() => onViewDetail(inc.incidentId)}>
                              重试
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* ─── Tab: Alerts / Incidents ─── */}
      {activeTab === 'alerts' && (
        <>
          {loading ? (
            <div style={{ padding: 60, textAlign: 'center', color: 'var(--muted)' }}>加载中...</div>
          ) : alerts.length === 0 ? (
            <div className="card" style={{ padding: 40, textAlign: 'center' }}>
              <div style={{ fontSize: 40, marginBottom: 16, opacity: 0.3 }}>🔔</div>
              <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--title)', marginBottom: 8 }}>
                暂无可运行 RCA 的告警
              </div>
              <div style={{ fontSize: 13, color: 'var(--muted)' }}>
                当前没有符合条件的告警（仅显示 SERVICE_ALERT 类型且可触发 RCA 的告警）
              </div>
            </div>
          ) : (
            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: 200 }}>Alert Name</th>
                    <th style={{ width: 130 }}>服务</th>
                    <th style={{ width: 110 }}>命名空间</th>
                    <th style={{ width: 80 }}>严重程度</th>
                    <th style={{ width: 140 }}>开始时间</th>
                    <th>摘要</th>
                    <th style={{ width: 110, textAlign: 'right' }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {alerts.map(alert => (
                    <tr key={alert.fingerprint}>
                      <td style={{ fontWeight: 600, fontSize: 13 }}>{alert.alertName}</td>
                      <td style={{ fontSize: 13 }}>{alert.service || '-'}</td>
                      <td style={{ fontSize: 12, color: 'var(--muted)' }}>{alert.namespace || '-'}</td>
                      <td><SeverityBadge severity={alert.severity} /></td>
                      <td style={{ fontFamily: 'monospace', fontSize: 11, whiteSpace: 'nowrap' }}>
                        {alert.startedAt ? alert.startedAt.replace('T', ' ').slice(0, 19) : '-'}
                      </td>
                      <td style={{ fontSize: 12, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {alert.summary || '-'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          className="btn btn-primary btn-sm"
                          style={{ fontSize: 11, padding: '2px 8px' }}
                          onClick={() => handleTriggerRca(alert)}
                          disabled={triggeringFp === alert.fingerprint}
                        >
                          {triggeringFp === alert.fingerprint ? '触发中...' : '运行 RCA'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/* ── Sub-components ── */

function SummaryCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="card" style={{ flex: 1, padding: '14px 18px' }}>
      <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, color }}>{value}</div>
    </div>
  )
}

function FilterSelect({ label, value, onChange, options }: {
  label: string
  value: string
  onChange: (v: string) => void
  options: Array<{ value: string; label: string }>
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ fontSize: 12, color: 'var(--muted)' }}>{label}:</span>
      <select className="form-select" style={{ width: 130, fontSize: 12, padding: '4px 8px' }}
        value={value} onChange={e => onChange(e.target.value)}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  )
}

function Badge({ label, bg, text }: { label: string; bg: string; text: string }) {
  return (
    <span style={{
      display: 'inline-block', padding: '2px 8px', borderRadius: 4,
      fontSize: 11, fontWeight: 600, background: bg, color: text,
      border: '1px solid ' + text,
    }}>
      {label}
    </span>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const info = SEVERITY_MAP[severity] || { label: severity || '-', color: '#667085' }
  return (
    <span style={{ fontSize: 12, fontWeight: 600, color: info.color }}>
      {info.label}
    </span>
  )
}
