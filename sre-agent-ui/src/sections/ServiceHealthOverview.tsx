import { useState, useEffect, useCallback } from 'react'
import {
  getServiceHealthSummary,
  ServiceHealthSummary,
  ServiceHealthView,
  ServiceHealthStatus,
} from '../api/client'
import { ChevronDown, RefreshCw } from 'lucide-react'
import { alerts } from '../data/mockData'

interface Props {
  onServiceClick: (name: string) => void
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

export default function ServiceHealthOverview({ onServiceClick }: Props) {
  const [summary, setSummary] = useState<ServiceHealthSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

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

  useEffect(() => {
    loadData()
  }, [loadData])

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

  return (
    <div>
      {/* Page Title + Time Controls */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">1 服务健康总览</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <button className="btn btn-ghost btn-sm" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            最近 5 分钟
            <ChevronDown size={14} />
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--muted)' }}>
            自动刷新：
            <div className="toggle active" style={{ transform: 'scale(0.8)' }}>
              <div className="toggle-knob" />
            </div>
          </div>
          <span style={{ fontSize: 13, color: 'var(--muted)' }}>
            更新时间：{formatTime(summary.checkedAt)}
          </span>
          <button
            className="btn btn-ghost btn-sm"
            onClick={loadData}
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

      {/* Mock indicator */}
      {hasMixed && (
        <div style={{ background: '#f0f4ff', border: '1px solid #b2ccff', borderRadius: 8, padding: '10px 16px', marginBottom: 16, fontSize: 13, color: '#3366cc' }}>
          ℹ 部分指标为 Mock Estimated（错误率、P95 延迟、流量、饱和度、重启数），服务可达性和 fault 状态来自真实 API
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
            告警数
            <span style={{ fontSize: 11, color: 'var(--orange)', marginLeft: 6, fontWeight: 400 }}>Mock</span>
          </div>
          <div className="kpi-value orange">{summary.alerts}</div>
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
              <th>错误率 (5m)<sup style={{ fontSize: 10, color: 'var(--orange)' }}>M</sup></th>
              <th>P95 延迟 (5m)<sup style={{ fontSize: 10, color: 'var(--orange)' }}>M</sup></th>
              <th>流量 (rps)<sup style={{ fontSize: 10, color: 'var(--orange)' }}>M</sup></th>
              <th>饱和度<sup style={{ fontSize: 10, color: 'var(--orange)' }}>M</sup></th>
              <th>最近重启<sup style={{ fontSize: 10, color: 'var(--orange)' }}>M</sup></th>
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
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px 0', gap: 0 }}>
            {svcs.map((svc, i) => {
              const isAbnormal = svc.status === 'down' || svc.status === 'degraded'
              const detailText = isAbnormal
                ? (svc.faultEnabled ? 'fault: ' + svc.faultType : (svc.errorRate || 'unreachable'))
                : (svc.p95Latency || '正常')
              return (
                <span key={svc.name}>
                  {/* Node */}
                  <div
                    className={'topo-node ' + topoNodeClass[svc.status]}
                    style={{ cursor: 'pointer' }}
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
                      <div style={{ padding: '0 12px' }}>
                        <svg width="60" height="20">
                          <line x1="0" y1="10" x2="48" y2="10" stroke={edgeColor} strokeWidth="2" />
                          <polygon points="48,5 58,10 48,15" fill={edgeColor} />
                        </svg>
                      </div>
                    )
                  })()}
                </span>
              )
            })}
          </div>
        </div>

        {/* Recent Alerts */}
        <div className="card" style={{ flex: '1' }}>
          <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            最近告警
            <span style={{ fontSize: 11, color: 'var(--orange)', fontWeight: 400 }}>Mock Alerts</span>
          </div>
          <div>
            {alerts.map((a, i) => (
              <div key={i} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '16px 0',
                borderTop: i > 0 ? '1px solid var(--line)' : 'none',
              }}>
                <span className="status-dot" style={{
                  background: a.level === 'P1' ? 'var(--red)' : 'var(--orange)',
                  flexShrink: 0,
                  width: 10,
                  height: 10,
                }} />
                <span style={{ flex: 1, fontSize: 14, color: 'var(--text)' }}>
                  {a.service} {a.message}
                </span>
                <span className={'badge ' + (a.level === 'P1' ? 'badge-red' : 'badge-orange')}>
                  {a.level}
                </span>
                <span style={{ fontSize: 13, color: 'var(--muted)', whiteSpace: 'nowrap' }}>{a.time}</span>
              </div>
            ))}
            <div style={{ marginTop: 16, textAlign: 'right' }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--blue)', cursor: 'pointer' }}>
                查看全部告警 →
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="footer-note">
        * 服务状态（reachable / fault）来自真实 API · 错误率 / 延迟 / 流量 / 饱和度 / 重启为 Mock Estimated · 告警为 Mock Alerts
      </div>
    </div>
  )
}
