import { useState, useEffect, useCallback } from 'react'
import {
  getEvidenceDrilldownForRun,
  getIncidents,
  EvidenceDrilldownView,
  EvidenceItemView,
  SourceSummaryView,
  EvidenceStrength,
  EvidenceSource,
  SourceStatus,
  type IncidentRcaResultView,
} from '../api/client'

/* ── Badge helpers ── */

const TYPE_BADGE_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  metric_error_rate_spike:       { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  metric_latency_p95_spike:      { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  metric_downstream_latency_spike: { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  metric_restart_rate_increased: { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  metric_memory_usage_high:      { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  metric_cpu_usage_high:         { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  log_downstream_timeout:        { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  log_timeout_error:             { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  log_exception_spike:           { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  log_exception:                 { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  log_http_5xx:                  { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  trace_downstream_span_slow:    { bg: '#e0efff', text: '#175cd3', border: '#84caff' },
  trace_child_span_dominates_latency: { bg: '#e0efff', text: '#175cd3', border: '#84caff' },
  trace_error_span:              { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  trace_root_span_slow:          { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  trace_timeout_span:            { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  container_crash_loop_backoff:  { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  pod_restart_count_increased:   { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  pod_not_ready:                 { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  container_oom_killed:          { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  alert_firing:                  { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  alert_severity_high:           { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
}

function TypeBadge({ type }: { type: string }) {
  const c = TYPE_BADGE_COLORS[type] || { bg: '#e0efff', text: '#175cd3', border: '#84caff' }
  return (
    <span style={{
      display: 'inline-block', padding: '2px 8px', borderRadius: 4,
      fontSize: 11, fontWeight: 600,
      background: c.bg, color: c.text, border: '1px solid ' + c.border,
      maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
    }}>
      {type}
    </span>
  )
}

function StrengthBadge({ strength }: { strength: EvidenceStrength }) {
  if (strength === 'strong') {
    return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600, background: '#dcfae6', color: '#027a48', border: '1px solid #75e0a7' }}>strong</span>
  }
  if (strength === 'moderate') {
    return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600, background: '#fef0c7', color: '#b54708', border: '1px solid #fedf89' }}>moderate</span>
  }
  if (strength === 'weak') {
    return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600, background: '#f2f4f7', color: '#667085', border: '1px solid #d9e1ec' }}>weak</span>
  }
  return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 11, color: '#667085', background: '#fff', border: '1px solid #d9e1ec' }}>-</span>
}

function StatusTag({ status }: { status: SourceStatus }) {
  if (status === 'available') {
    return <span className="badge badge-green" style={{ fontSize: 11 }}>有证据</span>
  }
  if (status === 'empty') {
    return <span className="badge" style={{ fontSize: 11, background: '#f2f4f7', border: '1px solid #d9e1ec', color: '#667085' }}>无异常信号</span>
  }
  if (status === 'unavailable') {
    return <span className="badge badge-red" style={{ fontSize: 11 }}>不可用</span>
  }
  return <span className="badge" style={{ fontSize: 11, background: '#f9fafb', border: '1px solid #eaecf0', color: '#98a2b3' }}>未知</span>
}

/* ── Source Matrix Row ── */

function SourceMatrixRow({ summary }: { summary: SourceSummaryView }) {
  return (
    <tr>
      <td style={{ fontWeight: 600, fontSize: 13 }}>{summary.displayName}</td>
      <td><StatusTag status={summary.status} /></td>
      <td style={{ textAlign: 'center', fontWeight: 700, color: summary.totalEvidence > 0 ? '#0b7285' : '#98a2b3' }}>
        {summary.totalEvidence}
      </td>
      <td style={{ textAlign: 'center', color: summary.effectiveEvidence > 0 ? 'var(--red)' : '#98a2b3' }}>
        {summary.effectiveEvidence}
      </td>
      <td style={{ textAlign: 'center', color: summary.noSignalEvidence > 0 ? '#667085' : '#98a2b3' }}>
        {summary.noSignalEvidence}
      </td>
      <td style={{ fontSize: 12 }}>
        {summary.topSignals.length > 0
          ? summary.topSignals.map((s, i) => (
            <span key={i} style={{
              display: 'inline-block', padding: '1px 6px', borderRadius: 3,
              fontSize: 10, fontWeight: 500, marginRight: 4, marginBottom: 2,
              background: '#f2f4f7', border: '1px solid #eaecf0', color: '#344054',
            }}>{s}</span>
          ))
          : <span style={{ color: '#98a2b3' }}>-</span>
        }
      </td>
      <td style={{ fontSize: 12, color: summary.error ? 'var(--red)' : '#667085' }}>
        {summary.error || summary.message || '-'}
      </td>
    </tr>
  )
}

/* ── Evidence Detail ── */

function EvidenceDetail({ item, onClose }: { item: EvidenceItemView; onClose: () => void }) {
  const attrs = item.attributes || {}
  const attrKeys = Object.keys(attrs)

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      background: 'rgba(0,0,0,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center',
      zIndex: 200,
    }} onClick={onClose}>
      <div className="card" style={{
        width: 640, maxHeight: '80vh', overflow: 'auto',
        padding: 24, display: 'flex', flexDirection: 'column', gap: 16,
      }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>证据详情</h3>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>关闭</button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '80px 1fr', gap: '8px 12px', fontSize: 13 }}>
          <span style={{ color: 'var(--muted)' }}>ID</span>
          <span style={{ fontFamily: 'monospace', fontSize: 12, wordBreak: 'break-all' }}>{item.id}</span>
          <span style={{ color: 'var(--muted)' }}>来源</span>
          <span>{item.source}</span>
          <span style={{ color: 'var(--muted)' }}>类型</span>
          <span><TypeBadge type={item.evidenceType} /></span>
          <span style={{ color: 'var(--muted)' }}>强度</span>
          <span><StrengthBadge strength={item.strength} /></span>
          {item.service && <>
            <span style={{ color: 'var(--muted)' }}>服务</span>
            <span>{item.service}</span>
          </>}
          {item.timestamp && <>
            <span style={{ color: 'var(--muted)' }}>时间</span>
            <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{item.timestamp}</span>
          </>}
        </div>

        <div>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 4 }}>内容</div>
          <div style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--text)', background: '#f9fafb', padding: 12, borderRadius: 8, border: '1px solid var(--line)' }}>
            {item.content}
          </div>
        </div>

        {attrKeys.length > 0 && (
          <div>
            <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 4 }}>属性</div>
            <div style={{ background: '#0b1522', color: '#d0d5dd', padding: 12, borderRadius: 8, fontSize: 12, fontFamily: 'monospace', maxHeight: 240, overflow: 'auto' }}>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                {JSON.stringify(attrs, null, 2)}
              </pre>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

/* ── Types ── */

type TabId = 'matrix' | 'top' | 'raw'

/* ── Main Panel ── */

export default function EvidenceDrilldownPanel() {
  const [data, setData] = useState<EvidenceDrilldownView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<TabId>('matrix')
  const [detailItem, setDetailItem] = useState<EvidenceItemView | null>(null)

  // V.2-UI-6.2: Run selector
  const [selectedRunId, setSelectedRunId] = useState<string>('')
  const [runs, setRuns] = useState<IncidentRcaResultView[]>([])
  const [runsLoading, setRunsLoading] = useState(true)

  // Fetch available RCA runs on mount
  useEffect(() => {
    (async () => {
      const result = await getIncidents()
      if (result.data) {
        const completed = result.data.filter(r =>
          r.status === 'COMPLETED' || r.status === 'NO_EVIDENCE_FOUND'
        )
        setRuns(completed)
      }
      setRunsLoading(false)
    })()
  }, [])

  // Raw evidence filters
  const [sourceFilter, setSourceFilter] = useState<string>('all')
  const [typeFilter, setTypeFilter] = useState<string>('all')
  const [showNoSignal, setShowNoSignal] = useState(false)
  const [showMetadata, setShowMetadata] = useState(false)
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 25

  const loadData = useCallback(async () => {
    if (!selectedRunId) return
    setLoading(true)
    setError(null)
    // V.2-UI-6.2: Load evidence for the selected RCA Run
    const result = await getEvidenceDrilldownForRun(selectedRunId)
    if (result.error) {
      setError(result.error)
      setData(null)
    } else {
      setData(result.data)
    }
    setLoading(false)
  }, [selectedRunId])

  useEffect(() => { loadData() }, [loadData])

  // Filtered raw evidence
  const filteredRaw = data
    ? data.rawEvidence.filter(e => {
        if (sourceFilter !== 'all' && e.source !== sourceFilter) return false
        if (typeFilter !== 'all' && e.evidenceType !== typeFilter) return false
        if (!showNoSignal && e.isNoSignal) return false
        if (!showMetadata && e.isMetadata) return false
        return true
      })
    : []

  const totalPages = Math.max(1, Math.ceil(filteredRaw.length / PAGE_SIZE))
  const pagedRaw = filteredRaw.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  // Unique types for filter
  const uniqueTypes = data
    ? [...new Set(data.rawEvidence.map(e => e.evidenceType))].sort()
    : []

  /* ── No run selected (default view) ── */
  if (!selectedRunId) {
    return (
      <div>
        <div className="breadcrumb" style={{ marginBottom: 4 }}>证据明细</div>
        <h1 className="page-title">4 证据明细（Evidence Drill-down）</h1>
        <div style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 16, opacity: 0.3 }}>🔍</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--title)', marginBottom: 8 }}>
            请选择一个 RCA Run 查看证据
          </div>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 20 }}>
            选择一次已完成的 RCA 分析，查看其证据明细
          </div>
          {runsLoading ? (
            <div style={{ fontSize: 13, color: 'var(--muted)' }}>加载运行列表...</div>
          ) : runs.length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--muted)' }}>
              暂无可用的 RCA Run，请先在「RCA 分析」页面运行一次分析
            </div>
          ) : (
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <select
                value={selectedRunId}
                onChange={e => {
                  const id = e.target.value
                  setSelectedRunId(id)
                  if (id) { setData(null); setError(null) }
                }}
                style={{
                  padding: '8px 16px',
                  borderRadius: 6,
                  border: '1px solid var(--border)',
                  fontSize: 14,
                  background: 'var(--surface)',
                  color: 'var(--text)',
                  minWidth: 280,
                }}
              >
                <option value="">-- 选择一个 RCA Run --</option>
                {runs.map(r => (
                  <option key={r.incidentId} value={r.incidentId}>
                    {r.incidentId} — {r.service || '未知服务'} ({r.status === 'COMPLETED' ? '已完成' : '无证据'})
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>
    )
  }

  /* ── Loading ── */
  if (loading) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: 'var(--muted)' }}>
        <div style={{ fontSize: 16, marginBottom: 8 }}>加载证据数据...</div>
        <div style={{ fontSize: 13 }}>正在从 RCA 分析结果获取证据</div>
      </div>
    )
  }

  /* ── Error ── */
  if (error) {
    return (
      <div style={{ padding: 40 }}>
        <div className="alert-banner red">
          <span className="alert-title">证据数据获取失败</span>
          <div style={{ marginTop: 4 }}>{error}</div>
        </div>
        <button className="btn btn-primary btn-sm" onClick={loadData}>重新加载</button>
      </div>
    )
  }

  /* ── Empty (selected run has no evidence) ── */
  if (!data) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <div style={{ fontSize: 40, marginBottom: 16, opacity: 0.3 }}>📋</div>
        <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--title)', marginBottom: 8 }}>
          该 RCA Run 暂无证据数据
        </div>
        <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>
          当前 Run ({selectedRunId}) 未返回证据明细，可能该 Run 状态为 NO_EVIDENCE_FOUND
        </div>
        <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 16 }}>
          请尝试切换其他 RCA Run，或重新运行一次 RCA 分析
        </div>
        <button className="btn btn-primary btn-sm" onClick={loadData}>刷新</button>
      </div>
    )
  }

  /* ── Tabs ── */
  const tabs: { id: TabId; label: string }[] = [
    { id: 'matrix', label: '来源矩阵' },
    { id: 'top', label: 'Top 证据' },
    { id: 'raw', label: '所有证据' },
  ]

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        证据明细 ＞ {selectedRunId ? `Run: ${selectedRunId}` : '所有证据'}
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <h1 className="page-title">4 证据明细（Evidence Drill-down）</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* V.2-UI-6.2: Run switcher */}
          <select
            value={selectedRunId}
            onChange={e => {
              const id = e.target.value
              setSelectedRunId(id)
              if (id) { setData(null); setError(null) }
            }}
            style={{
              padding: '4px 12px',
              borderRadius: 6,
              border: '1px solid var(--border)',
              fontSize: 12,
              background: 'var(--surface)',
              color: 'var(--text)',
              minWidth: 200,
            }}
          >
            <option value="">切换 Run...</option>
            {runs.map(r => (
              <option key={r.incidentId} value={r.incidentId}>
                {r.incidentId} — {r.service || '未知服务'}
              </option>
            ))}
          </select>
          <span style={{ fontSize: 12, color: 'var(--muted)' }}>
            {data.collectedAt ? '采集时间: ' + data.collectedAt.replace('T', ' ').replace('Z', '') : ''}
          </span>
          <button className="btn btn-ghost btn-sm" onClick={loadData} title="刷新">⟳</button>
        </div>
      </div>

      {/* KPI Summary */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>总证据</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#0b7285' }}>{data.totalEvidence}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>有效异常</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: data.effectiveEvidence > 0 ? 'var(--red)' : 'var(--green)' }}>{data.effectiveEvidence}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>数据来源</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--title)' }}>{data.sourceSummaries.filter(s => s.status === 'available').length}/{data.sourceSummaries.length}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>运行 ID</div>
          <div style={{ fontSize: 13, fontFamily: 'monospace', color: 'var(--text)', wordBreak: 'break-all', marginTop: 6 }}>{data.runId || '-'}</div>
        </div>
      </div>

      {/* Tab Bar */}
      <div style={{
        display: 'flex', gap: 24,
        borderBottom: '2px solid var(--line)', marginBottom: 16,
      }}>
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => { setActiveTab(tab.id); setPage(0) }}
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

      {/* ─── Tab: Source Matrix ─── */}
      {activeTab === 'matrix' && (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: 120 }}>来源</th>
                <th style={{ width: 90 }}>状态</th>
                <th style={{ width: 80, textAlign: 'center' }}>总证据数</th>
                <th style={{ width: 80, textAlign: 'center' }}>有效异常</th>
                <th style={{ width: 80, textAlign: 'center' }}>无信号</th>
                <th>主要信号</th>
                <th style={{ width: 200 }}>说明</th>
              </tr>
            </thead>
            <tbody>
              {data.sourceSummaries.map(s => (
                <SourceMatrixRow key={s.source} summary={s} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ─── Tab: Top Evidence ─── */}
      {activeTab === 'top' && (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          {data.topEvidence.length === 0 ? (
            <div style={{ padding: 32, textAlign: 'center', color: 'var(--muted)' }}>
              无有效异常证据（全部为 no_signal 或 metadata）
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th style={{ width: 80 }}>来源</th>
                  <th style={{ width: 170 }}>类型</th>
                  <th style={{ width: 120 }}>服务</th>
                  <th>内容摘要</th>
                  <th style={{ width: 70, textAlign: 'center' }}>强度</th>
                  <th style={{ width: 70, textAlign: 'center' }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {data.topEvidence.map(e => (
                  <tr key={e.id} style={{ cursor: 'pointer' }} onClick={() => setDetailItem(e)}>
                    <td style={{ fontSize: 13 }}>{e.source === 'tracing' ? 'Jaeger' : e.source}</td>
                    <td><TypeBadge type={e.evidenceType} /></td>
                    <td style={{ fontSize: 12, color: 'var(--muted)' }}>{e.service || '-'}</td>
                    <td style={{ fontSize: 13, maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {e.content}
                    </td>
                    <td style={{ textAlign: 'center' }}><StrengthBadge strength={e.strength} /></td>
                    <td style={{ textAlign: 'center' }}>
                      <button className="btn btn-ghost btn-sm" style={{ fontSize: 11 }}>详情</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* ─── Tab: Raw Evidence ─── */}
      {activeTab === 'raw' && (
        <>
          {/* Filters */}
          <div className="card" style={{ padding: '12px 16px', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--muted)' }}>来源:</span>
              <select className="form-select" style={{ width: 140, fontSize: 12, padding: '4px 8px' }}
                value={sourceFilter} onChange={e => { setSourceFilter(e.target.value); setPage(0) }}>
                <option value="all">全部</option>
                <option value="prometheus">Prometheus</option>
                <option value="loki">Loki</option>
                <option value="tracing">Jaeger / Tracing</option>
                <option value="kubernetes">Kubernetes</option>
                <option value="alertmanager">Alertmanager</option>
              </select>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 12, color: 'var(--muted)' }}>类型:</span>
              <select className="form-select" style={{ width: 200, fontSize: 12, padding: '4px 8px' }}
                value={typeFilter} onChange={e => { setTypeFilter(e.target.value); setPage(0) }}>
                <option value="all">全部</option>
                {uniqueTypes.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, cursor: 'pointer' }}>
              <input type="checkbox" checked={showNoSignal} onChange={e => { setShowNoSignal(e.target.checked); setPage(0) }} />
              显示 no_signal
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, cursor: 'pointer' }}>
              <input type="checkbox" checked={showMetadata} onChange={e => { setShowMetadata(e.target.checked); setPage(0) }} />
              显示 metadata
            </label>
            <span style={{ fontSize: 12, color: 'var(--muted)', marginLeft: 'auto' }}>
              筛选结果: {filteredRaw.length} 条
            </span>
          </div>

          {/* Raw table */}
          <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th style={{ width: 140 }}>时间</th>
                  <th style={{ width: 80 }}>来源</th>
                  <th style={{ width: 180 }}>类型</th>
                  <th style={{ width: 110 }}>服务</th>
                  <th>内容摘要</th>
                  <th style={{ width: 70, textAlign: 'center' }}>强度</th>
                  <th style={{ width: 50, textAlign: 'center' }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {pagedRaw.length === 0 ? (
                  <tr><td colSpan={7} style={{ textAlign: 'center', padding: 32, color: 'var(--muted)' }}>
                    无匹配证据
                  </td></tr>
                ) : pagedRaw.map(e => (
                  <tr key={e.id}
                    style={e.isNoSignal ? { opacity: 0.5 } : undefined}
                    onClick={() => setDetailItem(e)}
                  >
                    <td style={{ fontFamily: 'monospace', fontSize: 11, whiteSpace: 'nowrap' }}>
                      {e.timestamp ? e.timestamp.replace('T', ' ').replace('Z', '').slice(0, 19) : '-'}
                    </td>
                    <td style={{ fontSize: 12 }}>{e.source === 'tracing' ? 'Jaeger' : e.source}</td>
                    <td><TypeBadge type={e.evidenceType} /></td>
                    <td style={{ fontSize: 12, color: 'var(--muted)' }}>{e.service || '-'}</td>
                    <td style={{ fontSize: 12, maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {e.content}
                    </td>
                    <td style={{ textAlign: 'center' }}><StrengthBadge strength={e.strength} /></td>
                    <td style={{ textAlign: 'center' }}>
                      <button className="btn btn-ghost btn-sm" style={{ fontSize: 10, padding: '2px 6px' }}>详情</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
              <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                第 {page + 1} / {totalPages} 页 (每页 {PAGE_SIZE} 条)
              </span>
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(0)}>首页</button>
                <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>上一页</button>
                <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>下一页</button>
                <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(totalPages - 1)}>末页</button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Footer */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12, marginBottom: 8 }}>
        <span style={{ fontSize: 13, color: 'var(--muted)' }}>
          共 {data.totalEvidence} 条证据 (有效异常: {data.effectiveEvidence})
        </span>
        <button className="btn btn-ghost btn-sm">导出证据</button>
      </div>

      <div style={{ fontSize: 11, color: 'var(--green)', marginTop: 4 }}>
        * 数据来源: 真实 RCA 证据 (run: {data.runId || '-'})
      </div>

      {/* Evidence Detail Modal */}
      {detailItem && (
        <EvidenceDetail item={detailItem} onClose={() => setDetailItem(null)} />
      )}
    </div>
  )
}
