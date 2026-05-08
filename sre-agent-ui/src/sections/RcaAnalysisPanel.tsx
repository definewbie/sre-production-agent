import { useState, useEffect, useCallback } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip as RechartsTooltip,
  ResponsiveContainer, Cell,
} from 'recharts'
import {
  runLiveScenarioForRca,
  getIncidentRcaAnalysis,
  getIncident,
  triggerIncidentRca,
  getDecisionLabel,
  normalizeRcaRunStatus,
  type RcaAnalysisView,
  type HypothesisView,
  type IncidentContext,
  type RcaRunStatus,
  type IncidentRcaResultView,
} from '../api/client'
import RcaRunsList from './RcaRunsList'

/* ── Constants ── */

const STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '待分析',
  QUEUED: '排队中',
  RUNNING: '分析中',
  COMPLETED: '已完成',
  NO_EVIDENCE_FOUND: '证据不足',
  FAILED: '失败',
}

const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  NOT_STARTED: { bg: '#f2f4f7', text: '#667085' },
  QUEUED: { bg: '#fef0c7', text: '#b54708' },
  RUNNING: { bg: '#e0efff', text: '#175cd3' },
  COMPLETED: { bg: '#dcfae6', text: '#027a48' },
  NO_EVIDENCE_FOUND: { bg: '#fef0c7', text: '#b54708' },
  FAILED: { bg: '#fee4e2', text: '#b42318' },
}

const PROGRESS_STEPS = [
  { key: 'collect', label: '收集证据' },
  { key: 'analyze', label: '分析证据' },
  { key: 'evaluate', label: '评估假设' },
  { key: 'conclude', label: '生成结论' },
]

const NO_EVIDENCE_REASONS = [
  '时间窗口过小',
  '告警噪声或误报',
  '数据源不可用',
  '服务未被采集',
  '故障已经恢复',
]

const NO_EVIDENCE_ACTIONS = [
  '扩大时间窗口',
  '检查数据源',
  '重新分析',
  '查看环境状态',
]

type TabId = 'overview' | 'hypotheses' | 'evidence' | 'timeline' | 'events' | 'ai-suggestion' | 'metadata'

const TABS: { id: TabId; label: string }[] = [
  { id: 'overview', label: '概览' },
  { id: 'hypotheses', label: '候选假设' },
  { id: 'evidence', label: '证据链' },
  { id: 'timeline', label: '时间线' },
  { id: 'events', label: '事件' },
  { id: 'ai-suggestion', label: 'AI 建议' },
  { id: 'metadata', label: '元数据' },
]

/* ── Helpers ── */

function getDefaultJudgmentDetail(dt: RcaAnalysisView['decisionType']): string {
  switch (dt) {
    case 'likely_root_cause': return '已经基本锁定是哪个原因了，可以直接去处理。'
    case 'probable_root_cause': return '大概率是这个原因，建议再确认一下。'
    case 'competing_hypotheses': return '排名前两个的原因得分很接近，目前还没法确定到底是哪一个。'
    case 'uncertain_requires_more_evidence': return '现在掌握的线索还不够，需要继续查。'
    case 'insufficient_evidence': return '证据不足，没法做出判断。'
    default: return ''
  }
}

function getJudgmentColor(dt: RcaAnalysisView['decisionType']): string {
  switch (dt) {
    case 'likely_root_cause': return 'var(--green, #039855)'
    case 'probable_root_cause': return 'var(--blue, #0d6efd)'
    case 'competing_hypotheses': return 'var(--orange, #f79009)'
    case 'uncertain_requires_more_evidence': return 'var(--red, #f04438)'
    case 'insufficient_evidence': return 'var(--muted)'
    default: return 'var(--orange, #f79009)'
  }
}

function getHypothesisBadges(h: HypothesisView, isCompeting: boolean) {
  const badges: Array<{ text: string; color: string }> = []
  const level = (h.level || '').toUpperCase()
  if (level.includes('HIGH')) badges.push({ text: '高置信', color: 'green' })
  else if (level.includes('MEDIUM')) badges.push({ text: '中置信', color: 'orange' })
  else if (level.includes('LOW')) badges.push({ text: '低置信', color: 'red' })
  else badges.push({ text: '高置信', color: 'green' })
  if (h.supportingCount >= 3) badges.push({ text: '证据最集中', color: 'blue' })
  if (isCompeting && h.rank <= 2) badges.push({ text: '得分接近，需谨慎', color: 'orange' })
  return badges
}

function getExplanationStyle(text: string): { bg: string; tag: string; tagType: string } {
  const refutationKeywords = ['未发现', '未检测', '无异常', '正常', '反驳', '排除']
  if (refutationKeywords.some(kw => text.includes(kw))) {
    const match = text.match(/(OOM|CrashLoop|崩溃|资源|OOMKilled)/)
    return { bg: '#f6fffa', tag: match ? '反驳 ' + match[1] : '反驳', tagType: 'green' }
  }
  return { bg: '#eef6ff', tag: '强支持', tagType: 'blue' }
}

function formatDuration(ms?: number): string {
  if (!ms) return '--'
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return (ms / 60000).toFixed(1) + 'min'
}

function formatElapsed(startedAt?: string): string {
  if (!startedAt) return '--'
  const elapsed = Date.now() - new Date(startedAt).getTime()
  return formatDuration(elapsed)
}

/* ── Donut Chart ── */

function DonutChart({ total, sources }: { total: number; sources: Record<string, number> }) {
  const entries = Object.entries(sources)
  const colors = ['#12b76a', '#2e90fa', '#7a5af8', '#f79009', '#f04438']
  const radius = 72
  const strokeWidth = 22
  const cx = 130
  const cy = 130
  const circumference = 2 * Math.PI * radius
  let currentOffset = 0
  const arcs = entries.map(([name, count], i) => {
    const pct = count / total
    const dashLen = pct * circumference
    const dashGap = circumference - dashLen
    const arc = { name, count, color: colors[i % colors.length], strokeDasharray: dashLen.toFixed(1) + ' ' + dashGap.toFixed(1), strokeDashoffset: (-currentOffset).toFixed(1) }
    currentOffset += dashLen
    return arc
  })
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <svg width={cx * 2} height={cy * 2} viewBox={'0 0 ' + (cx * 2) + ' ' + (cy * 2)}>
        <circle cx={cx} cy={cy} r={radius} fill="none" stroke="#e5e7eb" strokeWidth={strokeWidth} />
        {arcs.map((arc, i) => (
          <circle key={i} cx={cx} cy={cy} r={radius} fill="none" stroke={arc.color} strokeWidth={strokeWidth}
            strokeDasharray={arc.strokeDasharray} strokeDashoffset={arc.strokeDashoffset}
            transform={'rotate(-90 ' + cx + ' ' + cy + ')'} />
        ))}
        <text x={cx} y={cy - 5} textAnchor="middle" style={{ fontSize: 32, fontWeight: 700, fill: '#111827' }}>{total}</text>
        <text x={cx} y={cy + 20} textAnchor="middle" style={{ fontSize: 13, fill: '#667085' }}>总计</text>
      </svg>
      <div style={{ fontSize: 12, color: 'var(--muted)', textAlign: 'center', lineHeight: 1.6, marginTop: 4 }}>
        {entries.map(([name, count]) => name + ' ' + count).join(' / ')}
      </div>
    </div>
  )
}

/* ── Incident Context Card ── */

function IncidentContextCard({ incident }: { incident: IncidentContext }) {
  return (
    <div className="card" style={{ padding: 20, marginBottom: 20, borderLeft: '4px solid #f04438' }}>
      <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>🔍 故障场景（本次分析就是针对这个故障的）</div>
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        {incident.service && (
          <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>故障服务</span>
            <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>{incident.service}</div></div>
        )}
        {incident.namespace && (
          <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>命名空间</span>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>{incident.namespace}</div></div>
        )}
        {incident.severity && (
          <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>严重程度</span>
            <div style={{ fontSize: 14, fontWeight: 700, color: incident.severity === 'critical' ? '#f04438' : incident.severity === 'warning' ? '#f79009' : 'var(--text)' }}>
              {incident.severity === 'critical' ? '严重' : incident.severity === 'warning' ? '警告' : incident.severity}</div></div>
        )}
        {incident.alertName && (
          <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>告警名称</span>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>{incident.alertName}</div></div>
        )}
      </div>
      {incident.description && (
        <div style={{ fontSize: 14, color: 'var(--text)', marginTop: 12, lineHeight: 1.6, background: '#f9fafb', padding: '8px 12px', borderRadius: 6 }}>{incident.description}</div>
      )}
    </div>
  )
}

/* ── Tabs Bar ── */

function TabBar({ active, onChange, tabs }: { active: TabId; onChange: (id: TabId) => void; tabs: typeof TABS }) {
  return (
    <div style={{ display: 'flex', borderBottom: '2px solid #e5e7eb', marginBottom: 20, gap: 0 }}>
      {tabs.map(t => (
        <button
          key={t.id}
          onClick={() => onChange(t.id)}
          style={{
            padding: '10px 20px', fontSize: 14, fontWeight: active === t.id ? 600 : 400,
            color: active === t.id ? 'var(--blue)' : 'var(--muted)',
            background: 'none', border: 'none', borderBottom: active === t.id ? '2px solid var(--blue)' : '2px solid transparent',
            marginBottom: -2, cursor: 'pointer', transition: 'color 0.15s',
          }}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}

/* ── Placeholder Tab ── */

function PlaceholderTab({ title }: { title: string }) {
  return (
    <div className="card" style={{ padding: 60, textAlign: 'center' }}>
      <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.2 }}>📋</div>
      <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--title)', marginBottom: 6 }}>{title}</div>
      <div style={{ fontSize: 14, color: 'var(--muted)' }}>该功能正在开发中，敬请期待。</div>
    </div>
  )
}

/* ── Bar Chart for Hypotheses ── */

function HypothesisBarChart({ hypotheses }: { hypotheses: HypothesisView[] }) {
  const data = hypotheses.map(h => ({
    name: h.name.length > 18 ? h.name.slice(0, 18) + '...' : h.name,
    fullName: h.name,
    score: +(h.score.toFixed(2)),
    rank: h.rank,
  }))
  const barColors = ['#f04438', '#f79009', '#7a5af8', '#2e90fa', '#12b76a']
  return (
    <div style={{ width: '100%', height: Math.max(180, data.length * 44) }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 30, left: 10, bottom: 0 }}
          barCategoryGap="20%">
          <XAxis type="number" domain={[0, 1]} tick={{ fontSize: 12, fill: '#667085' }} />
          <YAxis type="category" dataKey="name" width={160} tick={{ fontSize: 13, fill: 'var(--text)' }} />
          <RechartsTooltip
            formatter={(value: number) => [value.toFixed(2), 'Score']}
            labelFormatter={(label: string, payload: unknown[]) => {
              const item = (payload as Array<{ payload: { fullName: string } }>)?.[0]?.payload
              return item?.fullName || label
            }}
          />
          <Bar dataKey="score" radius={[0, 4, 4, 0]} maxBarSize={32}>
            {data.map((_, i) => (
              <Cell key={i} fill={barColors[i % barColors.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

/* ── Meta Info Row ── */

function MetaInfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ padding: '8px 0', borderBottom: '1px solid #f2f4f7', display: 'flex' }}>
      <span style={{ fontSize: 13, color: 'var(--muted)', width: 140, flexShrink: 0 }}>{label}</span>
      <span style={{ fontSize: 13, color: 'var(--text)' }}>{value || '-'}</span>
    </div>
  )
}

/* ══════════════════════════════════════════════
   Detail View
   ══════════════════════════════════════════════ */

function RcaRunDetail({
  incidentId,
  source,
  meta,
  labDemoResult,
  onBack,
}: {
  incidentId: string | null
  source: 'incident' | 'lab-demo'
  meta?: IncidentRcaResultView | null
  labDemoResult?: RcaAnalysisView | null
  onBack: () => void
}) {
  const [data, setData] = useState<RcaAnalysisView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const [rcaStatus, setRcaStatus] = useState<RcaRunStatus>('NOT_STARTED')
  const [statusError, setStatusError] = useState<string | null>(null)
  const [incidentMeta, setIncidentMeta] = useState<IncidentRcaResultView | null>(meta || null)
  const [activeTab, setActiveTab] = useState<TabId>('overview')

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (source === 'incident') {
        if (incidentId) {
          // Step 1: Get incident metadata (status)
          const metaResult = await getIncident(incidentId)
          if (metaResult.error) { setError(metaResult.error); return }
          if (metaResult.data) {
            const status = normalizeRcaRunStatus(metaResult.data.status)
            setRcaStatus(status)
            setIncidentMeta(metaResult.data)
            setStatusError(metaResult.data.errorMessage || null)
            // Step 2: If completed (with or without evidence), load full analysis
            if (status === 'COMPLETED' || status === 'NO_EVIDENCE_FOUND') {
              const result = await getIncidentRcaAnalysis(incidentId)
              if (result.error) { setError(result.error); return }
              setData(result.data)
            } else {
              setData(null)
            }
          } else {
            setRcaStatus('NOT_STARTED')
            setData(null)
          }
        } else {
          setRcaStatus('NOT_STARTED')
          setData(null)
        }
      } else {
        // Lab Demo: use preloaded result if available, otherwise NOT_STARTED
        if (labDemoResult) {
          setData(labDemoResult)
          setRcaStatus('COMPLETED')
        } else {
          setRcaStatus('NOT_STARTED')
          setData(null)
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '获取 RCA 数据失败')
    } finally {
      setLoading(false)
    }
  }, [incidentId, source, labDemoResult])

  useEffect(() => { fetchData() }, [fetchData])

  const handleTriggerRca = async () => {
    if (!incidentId) return
    setRunning(true)
    setError(null)
    try {
      const result = await triggerIncidentRca({ fingerprint: incidentMeta?.alertFingerprint, alertName: incidentMeta?.alertName, service: incidentMeta?.service })
      if (result.error) { setError(result.error) }
      else { setRcaStatus('RUNNING') }
    } catch (e) {
      setError(e instanceof Error ? e.message : '触发 RCA 失败')
    } finally {
      setRunning(false)
    }
  }

  const handleRerunLabDemo = async () => {
    setRunning(true)
    setError(null)
    try {
      const result = await runLiveScenarioForRca({ mode: 'live', faultMode: 'latency', waitSeconds: 30, lookbackSeconds: 300, stepSeconds: 15, runLlmProposal: true })
      if (result.error) { setError(result.error) }
      else { setData(result.data) }
    } catch (e) {
      setError(e instanceof Error ? e.message : '重新分析失败')
    } finally { setRunning(false) }
  }

  /* ── Loading ── */
  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 400 }}>
        <div style={{ fontSize: 16, color: 'var(--muted)' }}>加载 RCA 分析数据中...</div>
      </div>
    )
  }

  /* ── Breadcrumb + Header (shared) ── */
  const statusColor = STATUS_COLORS[rcaStatus] || STATUS_COLORS.NOT_STARTED
  const statusLabel = STATUS_LABELS[rcaStatus] || rcaStatus

  const breadcrumbHeader = (
    <div>
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        <span style={{ cursor: 'pointer', color: 'var(--blue)' }} onClick={onBack}>RCA 分析</span>
        <span style={{ color: 'var(--muted)', margin: '0 6px' }}>&gt;</span>
        <span style={{ color: 'var(--text)' }}>
          {source === 'lab-demo' ? 'Lab Demo' : (incidentMeta?.alertName || (incidentId ? incidentId.slice(0, 16) + '...' : '分析结果'))}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <h1 className="page-title" style={{ margin: 0 }}>
            {source === 'lab-demo' ? 'Lab Demo 分析结果' : 'RCA 分析结果'}
          </h1>
          <span style={{ fontSize: 12, padding: '2px 8px', borderRadius: 10, background: statusColor.bg, color: statusColor.text, fontWeight: 500 }}>
            {statusLabel}
          </span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {source === 'lab-demo' && (
            <button className="btn btn-ghost btn-sm" onClick={handleRerunLabDemo} disabled={running}>
              {running ? '分析中...' : '重新分析'}
            </button>
          )}
          <button className="btn btn-ghost btn-sm" onClick={onBack}>← 返回列表</button>
        </div>
      </div>
    </div>
  )

  /* ── Error ── */
  if (error && !data) {
    return (
      <div>
        {breadcrumbHeader}
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 16, color: '#f04438', marginBottom: 16 }}>加载失败：{error}</div>
          <button className="btn btn-primary btn-sm" onClick={fetchData} disabled={running}>重试</button>
        </div>
      </div>
    )
  }

  /* ═══ STATUS: NOT_STARTED ═══ */
  if (!data && rcaStatus === 'NOT_STARTED') {
    const eligible = source === 'lab-demo' || incidentMeta?.rcaEligible !== false
    const ineligibleReason = incidentMeta?.ineligibleReason
    return (
      <div>
        {breadcrumbHeader}
        {/* Incident/Alert info card */}
        {incidentMeta && (
          <div className="card" style={{ padding: 20, marginBottom: 20, borderLeft: '4px solid #667085' }}>
            <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>📋 告警信息</div>
            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
              {incidentMeta.alertName && (
                <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>告警名称</span>
                  <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--title)' }}>{incidentMeta.alertName}</div></div>
              )}
              {incidentMeta.service && (
                <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>服务</span>
                  <div style={{ fontSize: 14, color: 'var(--text)' }}>{incidentMeta.service}</div></div>
              )}
              {incidentMeta.severity && (
                <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>严重程度</span>
                  <div style={{ fontSize: 14, fontWeight: 700, color: incidentMeta.severity === 'critical' ? '#f04438' : 'var(--text)' }}>
                    {incidentMeta.severity}</div></div>
              )}
              {incidentMeta.namespace && (
                <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>命名空间</span>
                  <div style={{ fontSize: 14, color: 'var(--text)' }}>{incidentMeta.namespace}</div></div>
              )}
              {incidentMeta.startedAt && (
                <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>开始时间</span>
                  <div style={{ fontSize: 14, color: 'var(--text)' }}>{new Date(incidentMeta.startedAt).toLocaleString()}</div></div>
              )}
            </div>
            {incidentMeta.triggerSource && (
              <div style={{ marginTop: 12, fontSize: 13, color: 'var(--muted)' }}>
                触发来源：{incidentMeta.triggerSource}
              </div>
            )}
          </div>
        )}
        {/* Main prompt */}
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 40, marginBottom: 16, opacity: 0.3 }}>📋</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--title)', marginBottom: 8 }}>尚未运行 RCA 分析</div>
          <div style={{ fontSize: 14, color: 'var(--muted)', marginBottom: 20 }}>
            {source === 'incident'
              ? '当前告警未进行根因分析，请点击按钮开始分析。'
              : '暂无 Lab Demo 分析结果，请先运行一次。'}
          </div>
          {source === 'incident' ? (
            <div>
              <button
                className="btn btn-primary btn-sm"
                onClick={handleTriggerRca}
                disabled={running || !eligible}
                title={ineligibleReason || undefined}
                style={{ opacity: eligible ? 1 : 0.5 }}
              >
                {running ? '启动中...' : '运行 RCA'}
              </button>
              {ineligibleReason && (
                <div style={{ fontSize: 12, color: '#f79009', marginTop: 8 }}>{ineligibleReason}</div>
              )}
            </div>
          ) : (
            <button className="btn btn-primary btn-sm" onClick={handleRerunLabDemo} disabled={running}>
              {running ? '分析中...' : '运行 Lab Demo RCA'}
            </button>
          )}
        </div>
      </div>
    )
  }

  /* ═══ STATUS: RUNNING / QUEUED ═══ */
  if (!data && (rcaStatus === 'RUNNING' || rcaStatus === 'QUEUED')) {
    // Simulate which step is active based on a simple heuristic
    const startedMs = incidentMeta?.startedAt ? new Date(incidentMeta.startedAt).getTime() : null
    const elapsed = startedMs ? Date.now() - startedMs : 0
    const activeStepIdx = elapsed < 10000 ? 0 : elapsed < 25000 ? 1 : elapsed < 45000 ? 2 : 3

    return (
      <div>
        {breadcrumbHeader}
        {/* Top metadata */}
        {incidentMeta && (
          <div className="card" style={{ padding: 16, marginBottom: 20, display: 'flex', gap: 32, flexWrap: 'wrap', alignItems: 'center' }}>
            {incidentMeta.alertName && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>告警</span><div style={{ fontSize: 14, fontWeight: 600 }}>{incidentMeta.alertName}</div></div>}
            {incidentMeta.service && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>服务</span><div style={{ fontSize: 14 }}>{incidentMeta.service}</div></div>}
            <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>状态</span><div style={{ fontSize: 14, color: '#175cd3', fontWeight: 600 }}>分析中</div></div>
            {incidentMeta.startedAt && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>已用时间</span><div style={{ fontSize: 14 }}>{formatElapsed(incidentMeta.startedAt)}</div></div>}
            {incidentMeta.triggerSource && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>触发来源</span><div style={{ fontSize: 14 }}>{incidentMeta.triggerSource}</div></div>}
          </div>
        )}
        {/* Progress steps */}
        <div className="card" style={{ padding: 24, marginBottom: 20 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--title)', marginBottom: 20 }}>RCA 分析进行中...</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', position: 'relative' }}>
            {/* Connector line */}
            <div style={{
              position: 'absolute', top: 20, left: '10%', right: '10%', height: 2,
              background: '#e5e7eb', zIndex: 0,
            }} />
            {PROGRESS_STEPS.map((step, i) => {
              const isDone = i < activeStepIdx
              const isActive = i === activeStepIdx
              const isPending = i > activeStepIdx
              return (
                <div key={step.key} style={{ flex: 1, textAlign: 'center', position: 'relative', zIndex: 1 }}>
                  <div style={{
                    width: 40, height: 40, borderRadius: '50%', margin: '0 auto 8px',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 18,
                    background: isDone ? '#dcfae6' : isActive ? '#e0efff' : '#f9fafb',
                    border: '2px solid ' + (isDone ? '#027a48' : isActive ? '#175cd3' : '#e5e7eb'),
                    color: isDone ? '#027a48' : isActive ? '#175cd3' : '#d0d5dd',
                  }}>
                    {isDone ? '✓' : isActive ? '◉' : i + 1}
                  </div>
                  <div style={{
                    fontSize: 12, color: isDone ? '#027a48' : isActive ? '#175cd3' : '#98a2b3',
                    fontWeight: isActive ? 600 : 400,
                  }}>
                    {step.label}
                  </div>
                  {isActive && (
                    <div style={{ fontSize: 11, color: '#175cd3', marginTop: 4, opacity: 0.7 }}>进行中...</div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
        {/* Running log */}
        <div className="card" style={{ padding: 20, marginBottom: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--title)', marginBottom: 12 }}>📜 运行日志</div>
          <div style={{
            background: '#1e293b', color: '#e2e8f0', borderRadius: 8, padding: '12px 16px',
            fontFamily: 'monospace', fontSize: 12, lineHeight: 1.8, maxHeight: 200, overflowY: 'auto',
          }}>
            <div style={{ color: '#94a3b8' }}>$ 开始 RCA 分析流程...</div>
            {activeStepIdx >= 0 && <div style={{ color: '#60a5fa' }}>  正在从 Prometheus 收集指标...</div>}
            {activeStepIdx >= 0 && <div style={{ color: '#60a5fa' }}>  正在从 Loki 收集日志...</div>}
            {activeStepIdx >= 0 && <div style={{ color: '#60a5fa' }}>  正在从 Jaeger 收集 traces...</div>}
            {activeStepIdx >= 0 && <div style={{ color: '#60a5fa' }}>  正在从 Kubernetes 采集 Pod 状态...</div>}
            {activeStepIdx >= 1 && <div style={{ color: '#a78bfa' }}>  正在分析证据关联...</div>}
            {activeStepIdx >= 2 && <div style={{ color: '#f59e0b' }}>  正在评估候选假设...</div>}
            {activeStepIdx >= 3 && <div style={{ color: '#34d399' }}>  正在生成结论...</div>}
          </div>
          <div style={{ marginTop: 12, display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost btn-sm" onClick={fetchData}>⟳ 刷新状态</button>
          </div>
        </div>
      </div>
    )
  }

  /* ═══ STATUS: NO_EVIDENCE_FOUND ═══ */
  if (rcaStatus === 'NO_EVIDENCE_FOUND') {
    return (
      <div>
        {breadcrumbHeader}
        {/* Top metadata */}
        {incidentMeta && (
          <div className="card" style={{ padding: 16, marginBottom: 20, display: 'flex', gap: 32, flexWrap: 'wrap', alignItems: 'center' }}>
            {incidentMeta.alertName && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>告警</span><div style={{ fontSize: 14, fontWeight: 600 }}>{incidentMeta.alertName}</div></div>}
            {incidentMeta.service && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>服务</span><div style={{ fontSize: 14 }}>{incidentMeta.service}</div></div>}
            <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>状态</span><div style={{ fontSize: 14, color: '#b54708', fontWeight: 600 }}>无异常证据</div></div>
            {incidentMeta.startedAt && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>开始时间</span><div style={{ fontSize: 14 }}>{new Date(incidentMeta.startedAt).toLocaleString()}</div></div>}
            {incidentMeta.durationMs && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>耗时</span><div style={{ fontSize: 14 }}>{formatDuration(incidentMeta.durationMs)}</div></div>}
            {incidentMeta.triggerSource && <div><span style={{ fontSize: 12, color: 'var(--muted)' }}>触发来源</span><div style={{ fontSize: 14 }}>{incidentMeta.triggerSource}</div></div>}
          </div>
        )}
        {/* Main prompt */}
        <div className="card" style={{ padding: 32, marginBottom: 20, textAlign: 'center', borderTop: '3px solid #f79009' }}>
          <div style={{ fontSize: 40, marginBottom: 12 }}>🔍</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--title)', marginBottom: 8 }}>RCA 已完成，但未收集到异常证据</div>
          <div style={{ fontSize: 14, color: 'var(--muted)', marginBottom: 24, maxWidth: 500, margin: '0 auto 24px' }}>
            系统完成了本次分析流程，但没有发现足够的异常信号支撑明确 RCA 结论。
          </div>
          {/* Possible reasons */}
          <div className="card" style={{ padding: 20, marginBottom: 20, textAlign: 'left', background: '#fefce8' }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text)', marginBottom: 12 }}>可能原因</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {NO_EVIDENCE_REASONS.map((r, i) => (
                <span key={i} style={{ fontSize: 13, color: '#b54708', background: '#fff', padding: '4px 12px', borderRadius: 16, border: '1px solid #fed7aa' }}>{r}</span>
              ))}
            </div>
          </div>
          {/* Suggested actions */}
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 20 }}>
            {NO_EVIDENCE_ACTIONS.map((a, i) => (
              <button key={i} className="btn btn-ghost btn-sm" style={{ borderColor: '#d0d5dd' }}>
                {a}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
            {source === 'lab-demo' ? (
              <button className="btn btn-primary btn-sm" onClick={handleRerunLabDemo} disabled={running}>
                {running ? '分析中...' : '重新分析'}
              </button>
            ) : (
              <button className="btn btn-primary btn-sm" onClick={handleTriggerRca} disabled={running}>
                {running ? '启动中...' : '重新分析'}
              </button>
            )}
            <button className="btn btn-ghost btn-sm" onClick={onBack}>返回列表</button>
          </div>
        </div>
        {/* Source matrix (placeholder) */}
        <div className="card" style={{ padding: 20, marginBottom: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--title)', marginBottom: 12 }}>数据源状态</div>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>
            以下展示各数据源在本次分析中的可用情况。
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {['Prometheus', 'Loki', 'Jaeger', 'Kubernetes', 'Alertmanager'].map(src => (
              <div key={src} style={{
                padding: '10px 18px', borderRadius: 8, border: '1px solid #e5e7eb',
                fontSize: 13, background: '#f9fafb', display: 'flex', alignItems: 'center', gap: 8,
              }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#d0d5dd', display: 'inline-block' }} />
                <span style={{ color: 'var(--text)' }}>{src}</span>
                <span style={{ fontSize: 11, color: '#98a2b3' }}>empty</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  /* ═══ STATUS: FAILED ═══ */
  if (!data && rcaStatus === 'FAILED') {
    return (
      <div>
        {breadcrumbHeader}
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 40, marginBottom: 16 }}>❌</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: '#f04438', marginBottom: 8 }}>RCA 分析失败</div>
          {statusError && (
            <div style={{ fontSize: 13, color: 'var(--text)', marginBottom: 16, background: '#fef3f2', padding: '8px 16px', borderRadius: 8, textAlign: 'left', fontFamily: 'monospace' }}>
              {statusError}
            </div>
          )}
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
            {source === 'incident' ? (
              <button className="btn btn-primary btn-sm" onClick={handleTriggerRca} disabled={running}>
                {running ? '启动中...' : '重新分析'}
              </button>
            ) : (
              <button className="btn btn-primary btn-sm" onClick={handleRerunLabDemo} disabled={running}>
                {running ? '分析中...' : '重新分析'}
              </button>
            )}
            <button className="btn btn-ghost btn-sm" onClick={onBack}>返回列表</button>
          </div>
        </div>
      </div>
    )
  }

  /* ═══ STATUS: COMPLETED with data ═══ */
  if (!data) {
    // Fallback: shouldn't happen, but handle gracefully
    return (
      <div>
        {breadcrumbHeader}
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 16, color: 'var(--muted)', marginBottom: 16 }}>当前状态：{rcaStatus}（暂无数据）</div>
          <button className="btn btn-ghost btn-sm" onClick={onBack}>返回列表</button>
        </div>
      </div>
    )
  }

  /* ── Derived display values ── */
  const judgment = getDecisionLabel(data.decisionType)
  const judgmentDetail = data.competitionExplanation || getDefaultJudgmentDetail(data.decisionType)
  const analysisTime = formatDuration(data.durationMs)
  const evidenceSources: Record<string, number> = {}
  for (const [name, info] of Object.entries(data.evidenceSummary.sources)) {
    evidenceSources[name] = info.count
  }
  const aiTitle = data.aiProposal?.title || 'AI 建议'
  const aiAnalysis = data.aiProposal?.analysisMarkdown || ''
  const aiReasoning = data.aiProposal?.reasoning || ''
  const aiDetail = aiAnalysis || aiReasoning
  const aiSignals = data.aiProposal?.supportingSignals?.join('、') || ''
  const aiBoundary = data.aiProposal?.canAffectDecision
    ? '⚠ 此建议可能影响当前判定结果'
    : '说明：AI 只是提出一个可以验证的猜测，不会改变当前的分析结论。'
  const isCompeting = data.isCompeting
  const rankColors = ['#f04438', '#f79009']

  /* ── Tab content ── */
  const renderTabContent = () => {
    switch (activeTab) {
      /* —— 概览 —— */
      case 'overview':
        return (
          <div>
            {/* Incident Context */}
            {data.incident && <IncidentContextCard incident={data.incident} />}

            {/* Top Row: Judgment + Top Hypotheses */}
            <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
              {/* Judgment Card */}
              <div className="card" style={{ flex: '0 0 300px', padding: 20 }}>
                <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>当前判断</div>
                <div style={{ fontSize: 28, fontWeight: 700, color: getJudgmentColor(data.decisionType), marginBottom: 8 }}>{judgment}</div>
                <div style={{ fontSize: 14, color: 'var(--text)', lineHeight: 1.6 }}>{judgmentDetail}</div>
                <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <span className="badge badge-blue">置信度 {data.confidenceScore.toFixed(2)}</span>
                  {data.evidenceSummary.totalCount > 0 && (
                    <span className="badge badge-green">基于 {data.evidenceSummary.totalCount} 条证据</span>
                  )}
                  {isCompeting && <span className="badge badge-orange">竞争假设</span>}
                </div>
              </div>

              {/* Top Hypothesis Cards */}
              {data.hypotheses.slice(0, 2).map((h, i) => {
                const badges = getHypothesisBadges(h, isCompeting)
                return (
                  <div key={h.hypothesisId} className="card" style={{ flex: 1, padding: 20 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                      <div style={{ width: 28, height: 28, borderRadius: '50%', background: rankColors[i] || '#f04438', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700, flexShrink: 0 }}>{h.rank}</div>
                      <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)' }}>{h.name}</div>
                    </div>
                    <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--title)', marginBottom: 12 }}>{h.score.toFixed(2)}</div>
                    {h.explanation && <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5, marginBottom: 8 }}>{h.explanation}</div>}
                    <div style={{ display: 'flex', gap: 8 }}>
                      {badges.map((b, j) => <span key={j} className={'badge badge-' + b.color}>{b.text}</span>)}
                    </div>
                  </div>
                )
              })}
            </div>

            {/* Key Explanations + Evidence Overview */}
            <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
              <div className="card" style={{ flex: 2, padding: 20 }}>
                <div className="card-title">关键解释</div>
                <div style={{ marginTop: 12 }}>
                  {data.keyExplanations.length === 0 && <div style={{ fontSize: 14, color: 'var(--muted)' }}>暂无关键解释</div>}
                  {data.keyExplanations.map((text, i) => {
                    const styling = getExplanationStyle(text)
                    return (
                      <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: styling.bg, borderRadius: 8, padding: '8px 16px', marginBottom: 8 }}>
                        <span style={{ fontSize: 14, color: 'var(--text)' }}>● {text}</span>
                        <span className={'badge badge-' + styling.tagType} style={{ flexShrink: 0 }}>{styling.tag}</span>
                      </div>
                    )
                  })}
                </div>
              </div>
              <div className="card" style={{ flex: 1, padding: 20 }}>
                <div className="card-title">证据概览（有效证据）</div>
                <div style={{ marginTop: 8 }}>
                  <DonutChart total={data.evidenceSummary.totalCount} sources={evidenceSources} />
                </div>
              </div>
            </div>

            {/* Next Probes */}
            {data.nextProbes && data.nextProbes.length > 0 && (
              <div className="card" style={{ padding: 20, marginBottom: 20 }}>
                <div className="card-title">📌 建议下一步探测</div>
                <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {data.nextProbes.map((probe, i) => <span key={i} className="badge badge-blue">{probe}</span>)}
                </div>
              </div>
            )}
          </div>
        )

      /* —— 候选假设 —— */
      case 'hypotheses':
        return (
          <div>
            <div className="card" style={{ padding: 20, marginBottom: 20 }}>
              <div className="card-title" style={{ marginBottom: 16 }}>候选假设排名</div>
              <HypothesisBarChart hypotheses={data.hypotheses} />
            </div>
            {/* Detailed hypothesis list */}
            {data.hypotheses.map((h, i) => {
              const badges = getHypothesisBadges(h, isCompeting)
              return (
                <div key={h.hypothesisId} className="card" style={{ padding: 20, marginBottom: 12 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                    <div style={{ width: 28, height: 28, borderRadius: '50%', background: rankColors[i] || '#7a5af8', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700 }}>{h.rank}</div>
                    <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)', flex: 1 }}>{h.name}</div>
                    <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--title)', marginRight: 16 }}>{h.score.toFixed(2)}</div>
                    {badges.map((b, j) => <span key={j} className={'badge badge-' + b.color}>{b.text}</span>)}
                  </div>
                  {h.explanation && <div style={{ fontSize: 14, color: 'var(--text)', lineHeight: 1.6, background: '#f9fafb', padding: '8px 12px', borderRadius: 6 }}>{h.explanation}</div>}
                  <div style={{ display: 'flex', gap: 16, marginTop: 8, fontSize: 13, color: 'var(--muted)' }}>
                    <span>支持证据: {h.supportingCount}</span>
                    <span>反驳证据: {h.counterCount}</span>
                    <span>缺失证据: {h.missingCount}</span>
                    <span>矛盾: {h.contradictionCount}</span>
                  </div>
                </div>
              )
            })}
          </div>
        )

      /* —— Placeholder tabs —— */
      case 'evidence': return <PlaceholderTab title="证据链明细" />
      case 'timeline': return <PlaceholderTab title="事件时间线" />
      case 'events': return <PlaceholderTab title="相关事件" />

      /* —— AI 建议 —— */
      case 'ai-suggestion':
        return (
          <div>
            {data.aiProposal ? (
              <div className="card" style={{ padding: 20, marginBottom: 20 }}>
                <div className="card-title">🤖 AI 的猜测（仅供参考，不影响上面的结论）</div>
                <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)', marginTop: 12 }}>{aiTitle}</div>
                {aiDetail && (
                  <div style={{ fontSize: 14, color: 'var(--text)', marginTop: 8, lineHeight: 1.6, background: '#f9fafb', padding: '12px 16px', borderRadius: 8, whiteSpace: 'pre-wrap' }}>{aiDetail}</div>
                )}
                {aiSignals && (
                  <div style={{ fontSize: 14, color: 'var(--text)', marginTop: 8 }}><strong>相关信号：</strong>{aiSignals}</div>
                )}
                <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 8 }}>{aiBoundary}</div>
              </div>
            ) : (
              <PlaceholderTab title="AI 建议" />
            )}
          </div>
        )

      /* —— 元数据 —— */
      case 'metadata':
        return (
          <div className="card" style={{ padding: 20 }}>
            <div className="card-title" style={{ marginBottom: 16 }}>Run 元数据</div>
            <MetaInfoRow label="Run ID" value={data.runId || incidentId || '-'} />
            <MetaInfoRow label="场景名称" value={data.scenarioName || '-'} />
            <MetaInfoRow label="状态" value={data.status || statusLabel} />
            <MetaInfoRow label="阶段" value={data.phase || '-'} />
            <MetaInfoRow label="判定类型" value={judgment} />
            <MetaInfoRow label="置信度" value={data.confidenceScore.toFixed(2)} />
            <MetaInfoRow label="Score Gap" value={data.scoreGap.toFixed(2)} />
            <MetaInfoRow label="竞争假设" value={isCompeting ? '是' : '否'} />
            <MetaInfoRow label="证据总数" value={String(data.evidenceSummary.totalCount)} />
            <MetaInfoRow label="分析耗时" value={analysisTime} />
            <MetaInfoRow label="证据窗口" value={`wait=${data.evidenceWindow.waitSeconds}s / lookback=${data.evidenceWindow.lookbackSeconds}s / step=${data.evidenceWindow.stepSeconds}s`} />
            <MetaInfoRow label="数据来源" value={data.source} />
            {data.errorMessage && <MetaInfoRow label="错误信息" value={data.errorMessage} />}
          </div>
        )

      default:
        return null
    }
  }

  /* ── Render COMPLETED Detail ── */
  return (
    <div>
      {breadcrumbHeader}
      {/* Tabs */}
      <TabBar active={activeTab} onChange={setActiveTab} tabs={TABS} />
      {/* Content */}
      {renderTabContent()}
      {/* Footer note */}
      <div className="footer-note" style={{ marginTop: 20 }}>
        {data.source === 'real' ? '* 数据来自真实后端 API' : data.source === 'mixed' ? '* 部分数据来自模拟' : '* 所有数据为模拟数据，AI 建议仅供参考'}
      </div>
    </div>
  )
}

/* ══════════════════════════════════════════════
   Main Panel: List ↔ Detail Router
   ══════════════════════════════════════════════ */

interface RcaAnalysisPanelProps {
  alertIncidentId?: string | null
}

export default function RcaAnalysisPanel({ alertIncidentId }: RcaAnalysisPanelProps) {
  const [mode, setMode] = useState<'list' | 'detail'>(alertIncidentId ? 'detail' : 'list')
  const [detailIncidentId, setDetailIncidentId] = useState<string | null>(alertIncidentId || null)
  const [detailSource, setDetailSource] = useState<'incident' | 'lab-demo'>('incident')
  const [selectedMeta, setSelectedMeta] = useState<IncidentRcaResultView | null>(null)
  const [labDemoResult, setLabDemoResult] = useState<RcaAnalysisView | null>(null)

  const handleViewDetail = (incidentId: string, meta?: IncidentRcaResultView) => {
    setDetailIncidentId(incidentId)
    setDetailSource('incident')
    setSelectedMeta(meta || null)
    setLabDemoResult(null)
    setMode('detail')
  }

  const handleLabDemoResult = (result?: RcaAnalysisView) => {
    setDetailSource('lab-demo')
    setDetailIncidentId(null)
    setSelectedMeta(null)
    setLabDemoResult(result || null)
    setMode('detail')
  }

  const handleBackToList = () => {
    setMode('list')
  }

  if (mode === 'list') {
    return (
      <RcaRunsList
        onViewDetail={handleViewDetail}
        onLabDemoResult={handleLabDemoResult}
      />
    )
  }

  return (
    <RcaRunDetail
      incidentId={detailIncidentId}
      source={detailSource}
      meta={selectedMeta}
      labDemoResult={labDemoResult}
      onBack={handleBackToList}
    />
  )
}
