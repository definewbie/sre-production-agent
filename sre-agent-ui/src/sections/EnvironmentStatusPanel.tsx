import { useState, useEffect, useCallback } from 'react'
import {
  getEnvironmentSummary,
  triggerObservabilityCheck,
  type EnvironmentSummary,
  type ComponentStatus,
} from '../api/client'
import { environmentComponents as mockComponents } from '../data/mockData'

/* ── 状态映射 ── */

const statusLabel: Record<ComponentStatus, string> = {
  healthy: '正常',
  degraded: '降级',
  down: '不可用',
  unknown: '未知',
}

const statusBadge: Record<ComponentStatus, string> = {
  healthy: 'badge badge-green',
  degraded: 'badge badge-orange',
  down: 'badge badge-red',
  unknown: 'badge',
}

const statusBadgeExtra: Record<ComponentStatus, string> = {
  healthy: '',
  degraded: '',
  down: '',
  unknown: 'background:#f2f4f7;border-color:#d0d5dd;color:#667085;',
}

const statusDot: Record<ComponentStatus, string> = {
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

function formatCheckedAt(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleString('zh-CN', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return iso
  }
}

export default function EnvironmentStatusPanel() {
  const [summary, setSummary] = useState<EnvironmentSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const fetchData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true)
    else setLoading(true)
    setError(null)

    const result = await getEnvironmentSummary()

    if (result.data) {
      setSummary(result.data)
      // 如果有部分 error（一个 API 失败），保留但不阻止渲染
      if (result.error) {
        setError('部分数据获取失败: ' + result.error)
      }
    } else {
      setError(result.error || '环境状态获取失败')
      setSummary(null)
    }

    setLoading(false)
    setRefreshing(false)
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  const handleHealthCheck = async () => {
    setRefreshing(true)
    await triggerObservabilityCheck()
    await fetchData(true)
  }

  const handleRefresh = () => fetchData(true)

  // ── Loading ──
  if (loading) {
    return (
      <div>
        <div className="breadcrumb" style={{ marginBottom: 4 }}>
          环境状态
        </div>
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 16, color: 'var(--muted)', marginBottom: 12 }}>
            正在检查环境状态...
          </div>
          <div style={{ fontSize: 24 }}>⏳</div>
        </div>
      </div>
    )
  }

  // ── Error + 无数据 ──
  if (error && !summary) {
    return (
      <div>
        <div className="breadcrumb" style={{ marginBottom: 4 }}>
          环境状态
        </div>
        <div className="card" style={{ padding: 32 }}>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--red)', marginBottom: 12 }}>
            环境状态获取失败
          </div>
          <div style={{ fontSize: 14, color: 'var(--text)', marginBottom: 16, fontFamily: 'monospace', background: '#fff1f3', padding: 12, borderRadius: 8 }}>
            {error}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary btn-sm" onClick={handleRefresh}>
              重新检查
            </button>
          </div>
          <div style={{ marginTop: 16, padding: 12, background: '#fef0c7', borderRadius: 8, fontSize: 13, color: '#b54708' }}>
            ⚠ 后端 API 未启动或不可达。请确认 SRE Agent Server (localhost:8080) 已启动。
          </div>
        </div>
      </div>
    )
  }

  // ── 使用数据（real 或 fallback） ──
  const components = summary?.components || []

  // 如果 summary 为空但没 error（理论上不会到这）
  if (components.length === 0) {
    return (
      <div>
        <div className="breadcrumb" style={{ marginBottom: 4 }}>环境状态</div>
        <div className="card" style={{ padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: 14, color: 'var(--muted)' }}>暂无环境状态数据</div>
          <button className="btn btn-primary btn-sm" style={{ marginTop: 16 }} onClick={handleRefresh}>
            刷新
          </button>
        </div>
      </div>
    )
  }

  const totalCount = summary?.total || components.length
  const healthyCount = summary?.healthyCount ?? components.filter(c => c.status === 'healthy').length
  const degradedCount = summary?.degradedCount ?? components.filter(c => c.status === 'degraded').length
  const downCount = summary?.downCount ?? components.filter(c => c.status === 'down').length
  const overallOk = summary?.overallStatus === 'healthy'
  const checkedAt = summary?.checkedAt ? formatCheckedAt(summary.checkedAt) : '-'

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        环境状态 ＞ 可观测性组件 / Demo Services / SRE Agent API
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">6 环境状态</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-ghost btn-sm" onClick={handleRefresh} disabled={refreshing}>
            {refreshing ? '刷新中...' : '刷新'}
          </button>
          <button className="btn btn-primary btn-sm" onClick={handleHealthCheck} disabled={refreshing}>
            运行健康检查
          </button>
        </div>
      </div>

      {/* Partial error warning */}
      {error && (
        <div style={{ padding: '10px 16px', borderRadius: 8, fontSize: 13, marginBottom: 16, background: '#fff7ed', border: '1px solid #fed7aa', color: '#b54708' }}>
          ⚠ {error}
        </div>
      )}

      {/* KPI Summary Cards */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>组件总数</div>
          <div style={{ fontSize: 32, fontWeight: 700 }}>{totalCount}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>正常组件</div>
          <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--green)' }}>{healthyCount}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>异常组件</div>
          <div style={{ fontSize: 32, fontWeight: 700, color: (degradedCount + downCount) > 0 ? 'var(--red)' : 'var(--text)' }}>{degradedCount + downCount}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>最近检查</div>
          <div style={{ fontSize: 20, fontWeight: 700 }}>{checkedAt}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>环境状态</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: overallOk ? 'var(--green)' : (downCount > 0 ? 'var(--red)' : 'var(--orange)') }}>
            {overallOk ? 'Ready' : (downCount > 0 ? 'Error' : 'Degraded')}
          </div>
        </div>
      </div>

      {/* Component Table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden', marginBottom: 20 }}>
        <div style={{ padding: '16px 20px 12px' }}>
          <div className="card-title">基础设施组件状态</div>
        </div>
        <table className="data-table">
          <thead>
            <tr>
              <th style={{ width: 140 }}>组件</th>
              <th style={{ width: 80 }}>状态</th>
              <th>Endpoint / Cluster</th>
              <th style={{ width: 100 }}>响应时间</th>
              <th style={{ width: 130 }}>最近检查</th>
              <th style={{ width: 70, textAlign: 'center' }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {components.map((c, i) => (
              <tr key={i} style={c.status === 'down' ? { background: '#fff9f9' } : undefined}>
                <td style={{ fontWeight: 600 }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <span className={'status-dot ' + statusDot[c.status]} style={c.status === 'unknown' ? { background: '#d0d5dd' } : undefined} />
                    {c.name}
                  </span>
                </td>
                <td>
                  <span className={statusBadge[c.status]} style={statusBadgeExtra[c.status] ? { background: '#f2f4f7', borderColor: '#d0d5dd', color: '#667085' } : undefined}>
                    {statusLabel[c.status]}
                  </span>
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{c.endpoint}</td>
                <td>{c.responseTimeMs > 0 ? c.responseTimeMs + 'ms' : '-'}</td>
                <td>{formatTime(c.lastCheckedAt)}</td>
                <td style={{ textAlign: 'center' }}>
                  {c.endpoint && c.endpoint.startsWith('http') ? (
                    <a href={c.endpoint} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm" style={{ padding: '2px 8px', fontSize: 13, textDecoration: 'none' }}>
                      打开
                    </a>
                  ) : (
                    <span style={{ fontSize: 12, color: 'var(--muted)' }}>-</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Bottom Row */}
      <div style={{ display: 'flex', gap: 20 }}>
        {/* Diagnosis Notes */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title">环境诊断说明</div>
          <div style={{ marginTop: 12, lineHeight: 2 }}>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>
              • 环境状态用于判断 no_signal 是否由采集组件异常导致。
            </div>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>
              • Prometheus / Loki / Jaeger 均正常时，RCA 证据可信度更高。
            </div>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>
              • 如果某组件异常，RCA 页面必须显式提示该 source 不可靠。
            </div>
          </div>
        </div>

        {/* Quick Actions */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title">快速操作</div>
          <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
            <button className="btn btn-primary btn-sm" onClick={handleRefresh} disabled={refreshing}>
              刷新状态
            </button>
            <button className="btn btn-ghost btn-sm" onClick={() => window.open('http://localhost:3000', '_blank')}>
              打开 Grafana
            </button>
            <button className="btn btn-ghost btn-sm" onClick={() => window.open('http://localhost:9090', '_blank')}>
              查看 Prometheus
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
