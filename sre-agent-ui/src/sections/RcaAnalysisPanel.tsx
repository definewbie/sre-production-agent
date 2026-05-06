import { useState, useEffect, useCallback } from 'react'
import {
  getLatestLiveScenario,
  runLiveScenarioForRca,
  simulateLiveScenario,
  getDecisionLabel,
  type RcaAnalysisView,
  type HypothesisView,
  type IncidentContext,
} from '../api/client'

/* ── Helpers ── */

/** Decision type → 通俗解释 */
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

/** Decision type → judgment card color */
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

/** Derive badges for a hypothesis card */
function getHypothesisBadges(h: HypothesisView, isCompeting: boolean) {
  const badges: Array<{ text: string; color: string }> = []

  const level = (h.level || '').toUpperCase()
  if (level.includes('HIGH')) {
    badges.push({ text: '高置信', color: 'green' })
  } else if (level.includes('MEDIUM')) {
    badges.push({ text: '中置信', color: 'orange' })
  } else if (level.includes('LOW')) {
    badges.push({ text: '低置信', color: 'red' })
  } else {
    badges.push({ text: '高置信', color: 'green' })
  }

  if (h.supportingCount >= 3) {
    badges.push({ text: '证据最集中', color: 'blue' })
  }

  if (isCompeting && h.rank <= 2) {
    badges.push({ text: '得分接近，需谨慎', color: 'orange' })
  }

  return badges
}

/** Derive explanation row style from text content */
function getExplanationStyle(text: string): { bg: string; tag: string; tagType: string } {
  const refutationKeywords = ['未发现', '未检测', '无异常', '正常', '反驳', '排除']
  if (refutationKeywords.some(kw => text.includes(kw))) {
    const match = text.match(/(OOM|CrashLoop|崩溃|资源|OOMKilled)/)
    return { bg: '#f6fffa', tag: match ? '反驳 ' + match[1] : '反驳', tagType: 'green' }
  }
  return { bg: '#eef6ff', tag: '强支持', tagType: 'blue' }
}

/* ── Donut Chart (unchanged) ── */

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
    const arc = {
      name,
      count,
      color: colors[i % colors.length],
      strokeDasharray: dashLen.toFixed(1) + ' ' + dashGap.toFixed(1),
      strokeDashoffset: (-currentOffset).toFixed(1),
    }
    currentOffset += dashLen
    return arc
  })

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <svg width={cx * 2} height={cy * 2} viewBox={'0 0 ' + (cx * 2) + ' ' + (cy * 2)}>
        <circle cx={cx} cy={cy} r={radius} fill="none" stroke="#e5e7eb" strokeWidth={strokeWidth} />
        {arcs.map((arc, i) => (
          <circle
            key={i}
            cx={cx} cy={cy} r={radius}
            fill="none" stroke={arc.color} strokeWidth={strokeWidth}
            strokeDasharray={arc.strokeDasharray} strokeDashoffset={arc.strokeDashoffset}
            transform={'rotate(-90 ' + cx + ' ' + cy + ')'}
          />
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
      <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>
        🔍 故障场景（本次分析就是针对这个故障的）
      </div>
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        {incident.service && (
          <div>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>故障服务</span>
            <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>{incident.service}</div>
          </div>
        )}
        {incident.namespace && (
          <div>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>命名空间</span>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>{incident.namespace}</div>
          </div>
        )}
        {incident.severity && (
          <div>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>严重程度</span>
            <div style={{ fontSize: 14, fontWeight: 700, color: incident.severity === 'critical' ? '#f04438' : incident.severity === 'warning' ? '#f79009' : 'var(--text)' }}>
              {incident.severity === 'critical' ? '严重' : incident.severity === 'warning' ? '警告' : incident.severity}
            </div>
          </div>
        )}
        {incident.alertName && (
          <div>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>告警名称</span>
            <div style={{ fontSize: 14, color: 'var(--text)' }}>{incident.alertName}</div>
          </div>
        )}
      </div>
      {incident.description && (
        <div style={{ fontSize: 14, color: 'var(--text)', marginTop: 12, lineHeight: 1.6, background: '#f9fafb', padding: '8px 12px', borderRadius: 6 }}>
          {incident.description}
        </div>
      )}
    </div>
  )
}

/* ── Main Component ── */

export default function RcaAnalysisPanel() {
  const [data, setData] = useState<RcaAnalysisView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // 1) Try to get latest result
      const latest = await getLatestLiveScenario()
      if (latest.error) {
        setError(latest.error)
        return
      }
      if (latest.data) {
        setData(latest.data)
        return
      }
      // 2) No existing result → run simulation
      const sim = await simulateLiveScenario(true)
      if (sim.error) {
        setError(sim.error)
      } else {
        setData(sim.data)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '获取 RCA 数据失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  const handleRerun = async () => {
    setRunning(true)
    setError(null)
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
        setData(result.data)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '重新分析失败')
    } finally {
      setRunning(false)
    }
  }

  /* ── Loading ── */
  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 400 }}>
        <div style={{ fontSize: 16, color: 'var(--muted)' }}>加载 RCA 分析数据中...</div>
      </div>
    )
  }

  /* ── Error ── */
  if (error && !data) {
    return (
      <div>
        <div className="breadcrumb" style={{ marginBottom: 4 }}>RCA 分析 ＞ 分析结果</div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
          <h1 className="page-title">3 RCA 分析结果</h1>
        </div>
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 16, color: '#f04438', marginBottom: 16 }}>加载失败：{error}</div>
          <button className="btn btn-primary btn-sm" onClick={fetchData} disabled={running}>重试</button>
        </div>
      </div>
    )
  }

  /* ── No data ── */
  if (!data) {
    return (
      <div>
        <div className="breadcrumb" style={{ marginBottom: 4 }}>RCA 分析 ＞ 分析结果</div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
          <h1 className="page-title">3 RCA 分析结果</h1>
        </div>
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 16, color: 'var(--muted)', marginBottom: 16 }}>暂无 RCA 分析数据</div>
          <button className="btn btn-primary btn-sm" onClick={handleRerun} disabled={running}>
            {running ? '分析中...' : '运行模拟分析'}
          </button>
        </div>
      </div>
    )
  }

  /* ── Derived display values ── */

  const judgment = getDecisionLabel(data.decisionType)
  const judgmentDetail = data.competitionExplanation || getDefaultJudgmentDetail(data.decisionType)
  const analysisTime = data.durationMs ? `${(data.durationMs / 1000).toFixed(1)}s` : '--'

  // Flatten evidence sources for DonutChart
  const evidenceSources: Record<string, number> = {}
  for (const [name, info] of Object.entries(data.evidenceSummary.sources)) {
    evidenceSources[name] = info.count
  }

  // AI Advisory — 优先展示 analysisMarkdown（LLM 完整分析），其次 reasoning
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

  /* ── Render ── */

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        RCA 分析 ＞ 分析结果
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">3 RCA 分析结果</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 13, color: 'var(--muted)' }}>分析时间：{analysisTime}</span>
          <button className="btn btn-ghost btn-sm" onClick={handleRerun} disabled={running}>
            {running ? '分析中...' : '重新分析'}
          </button>
          <button className="btn btn-primary btn-sm" disabled={running}>生成报告</button>
        </div>
      </div>

      {/* Fix 2: Incident Context Card */}
      {data.incident && <IncidentContextCard incident={data.incident} />}

      {/* Top Row: Judgment + Hypothesis Cards */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
        {/* Judgment Card */}
        <div className="card" style={{ flex: '0 0 300px', padding: 20 }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>当前判断</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: getJudgmentColor(data.decisionType), marginBottom: 8 }}>
            {judgment}
          </div>
          <div style={{ fontSize: 14, color: 'var(--text)', lineHeight: 1.6 }}>
            {judgmentDetail}
          </div>
        </div>

        {/* Hypothesis Cards */}
        {data.hypotheses.map((h, i) => {
          const badges = getHypothesisBadges(h, isCompeting)
          return (
            <div key={h.hypothesisId} className="card" style={{ flex: 1, padding: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                <div style={{
                  width: 28, height: 28, borderRadius: '50%',
                  background: rankColors[i] || '#f04438',
                  color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 13, fontWeight: 700, flexShrink: 0,
                }}>
                  {h.rank}
                </div>
                <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)' }}>{h.name}</div>
              </div>
              <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--title)', marginBottom: 12 }}>
                {h.score.toFixed(2)}
              </div>
              {/* Fix 5: Show hypothesis explanation from backend */}
              {h.explanation && (
                <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5, marginBottom: 8 }}>
                  {h.explanation}
                </div>
              )}
              <div style={{ display: 'flex', gap: 8 }}>
                {badges.map((b, j) => (
                  <span key={j} className={'badge badge-' + b.color}>{b.text}</span>
                ))}
              </div>
            </div>
          )
        })}
      </div>

      {/* Middle Row: Key Explanations + Evidence Overview */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
        {/* Key Explanations */}
        <div className="card" style={{ flex: 2, padding: 20 }}>
          <div className="card-title">关键解释</div>
          <div style={{ marginTop: 12 }}>
            {data.keyExplanations.length === 0 && (
              <div style={{ fontSize: 14, color: 'var(--muted)' }}>暂无关键解释</div>
            )}
            {data.keyExplanations.map((text, i) => {
              const styling = getExplanationStyle(text)
              return (
                <div key={i} style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  background: styling.bg, borderRadius: 8, padding: '8px 16px', marginBottom: 8,
                }}>
                  <span style={{ fontSize: 14, color: 'var(--text)' }}>● {text}</span>
                  <span className={'badge badge-' + styling.tagType} style={{ flexShrink: 0 }}>
                    {styling.tag}
                  </span>
                </div>
              )
            })}
          </div>
        </div>

        {/* Evidence Overview with Donut */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title">证据概览（有效证据）</div>
          <div style={{ marginTop: 8 }}>
            <DonutChart
              total={data.evidenceSummary.totalCount}
              sources={evidenceSources}
            />
          </div>
        </div>
      </div>

      {/* Fix 3: AI Advisory Banner — 展示完整 LLM 分析 */}
      {data.aiProposal && (
        <div className="card" style={{ padding: 20, marginBottom: 20 }}>
          <div className="card-title">🤖 AI 的猜测（仅供参考，不影响上面的结论）</div>
          <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)', marginTop: 12 }}>
            {aiTitle}
          </div>
          {aiDetail && (
            <div style={{
              fontSize: 14, color: 'var(--text)', marginTop: 8, lineHeight: 1.6,
              background: '#f9fafb', padding: '12px 16px', borderRadius: 8,
              whiteSpace: 'pre-wrap',
            }}>
              {aiDetail}
            </div>
          )}
          {aiSignals && (
            <div style={{ fontSize: 14, color: 'var(--text)', marginTop: 8 }}>
              <strong>相关信号：</strong>{aiSignals}
            </div>
          )}
          <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 8 }}>
            {aiBoundary}
          </div>
        </div>
      )}

      {/* Next Probes */}
      {data.nextProbes && data.nextProbes.length > 0 && (
        <div className="card" style={{ padding: 20, marginBottom: 20 }}>
          <div className="card-title">📌 建议下一步探测</div>
          <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {data.nextProbes.map((probe, i) => (
              <span key={i} className="badge badge-blue">{probe}</span>
            ))}
          </div>
        </div>
      )}

      <div className="footer-note">
        {data.source === 'real' ? '* 数据来自真实后端 API' : data.source === 'mixed' ? '* 部分数据来自模拟' : '* 所有数据为模拟数据，AI 建议仅供参考'}
      </div>
    </div>
  )
}
