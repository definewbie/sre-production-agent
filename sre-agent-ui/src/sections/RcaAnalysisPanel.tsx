import { rcaResult } from '../data/mockData'

// Simple donut chart via SVG arcs
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
        {/* Background track */}
        <circle cx={cx} cy={cy} r={radius} fill="none" stroke="#e5e7eb" strokeWidth={strokeWidth} />
        {/* Arcs */}
        {arcs.map((arc, i) => (
          <circle
            key={i}
            cx={cx}
            cy={cy}
            r={radius}
            fill="none"
            stroke={arc.color}
            strokeWidth={strokeWidth}
            strokeDasharray={arc.strokeDasharray}
            strokeDashoffset={arc.strokeDashoffset}
            transform={'rotate(-90 ' + cx + ' ' + cy + ')'}
          />
        ))}
        {/* Center text */}
        <text x={cx} y={cy - 5} textAnchor="middle" style={{ fontSize: 32, fontWeight: 700, fill: '#111827' }}>{total}</text>
        <text x={cx} y={cy + 20} textAnchor="middle" style={{ fontSize: 13, fill: '#667085' }}>总计</text>
      </svg>
      <div style={{ fontSize: 12, color: 'var(--muted)', textAlign: 'center', lineHeight: 1.6, marginTop: 4 }}>
        {entries.map(([name, count]) => name + ' ' + count).join(' / ')}
      </div>
    </div>
  )
}

export default function RcaAnalysisPanel() {
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
          <span style={{ fontSize: 13, color: 'var(--muted)' }}>分析时间：{rcaResult.analysisTime}</span>
          <button className="btn btn-ghost btn-sm">重新分析</button>
          <button className="btn btn-primary btn-sm">生成报告</button>
        </div>
      </div>

      {/* Top Row: Judgment + Hypothesis Cards (3 cards) */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
        {/* Judgment Card */}
        <div className="card" style={{ flex: '0 0 300px', padding: 20 }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 12 }}>当前判断</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--orange)', marginBottom: 8 }}>
            {rcaResult.judgment}
          </div>
          <div style={{ fontSize: 14, color: 'var(--text)', lineHeight: 1.6 }}>
            {rcaResult.judgmentDetail}
          </div>
        </div>

        {/* Hypothesis Cards */}
        {rcaResult.hypotheses.map((h, i) => {
          const rankColors = ['#f04438', '#f79009']
          return (
            <div key={h.rank} className="card" style={{ flex: 1, padding: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                <div style={{
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  background: rankColors[i] || '#f04438',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 13,
                  fontWeight: 700,
                  flexShrink: 0,
                }}>
                  {h.rank}
                </div>
                <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)' }}>{h.name}</div>
              </div>
              <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--title)', marginBottom: 12 }}>
                {h.score.toFixed(2)}
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <span className="badge badge-green">{h.confidence}</span>
                {h.tags.map((t, j) => (
                  <span key={j} className={'badge badge-' + h.tagColors[j]}>{t}</span>
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
            {rcaResult.explanations.map((e, i) => {
              const isHighlight = e.bg === 'highlight'
              return (
                <div key={i} style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: isHighlight ? '#eef6ff' : '#f6fffa',
                  borderRadius: 8,
                  padding: '8px 16px',
                  marginBottom: 8,
                }}>
                  <span style={{ fontSize: 14, color: 'var(--text)' }}>
                    ● {e.text}
                  </span>
                  <span className={'badge badge-' + e.tagType} style={{ flexShrink: 0 }}>
                    {e.tag}
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
              total={rcaResult.evidenceOverview.total}
              sources={rcaResult.evidenceOverview.sources}
            />
          </div>
        </div>
      </div>

      {/* AI Advisory Banner */}
      <div className="card" style={{ padding: 20 }}>
        <div className="card-title">AI 假设建议（未验证，不改变 RCA 结论）</div>
        <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--title)', marginTop: 12 }}>
          {rcaResult.aiAdvisory.title}
        </div>
        <div style={{ fontSize: 14, color: 'var(--text)', marginTop: 8, lineHeight: 1.6 }}>
          {rcaResult.aiAdvisory.detail}
        </div>
        <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 8 }}>
          {rcaResult.aiAdvisory.boundary}
        </div>
      </div>

      <div className="footer-note">* 所有数据为模拟数据，AI 建议仅供参考</div>
    </div>
  )
}
