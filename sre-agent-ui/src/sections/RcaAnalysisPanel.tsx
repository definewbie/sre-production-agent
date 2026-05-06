import { rcaResult } from '../data/mockData'
import { Sparkles, Timer, FileSearch } from 'lucide-react'

export default function RcaAnalysisPanel() {
  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">RCA 分析结果</h1>
          <div className="page-subtitle">order-service 异常 · 分析完成</div>
        </div>
        <div className="flex items-center gap-12">
          <div style={{ fontSize: 13, color: 'var(--muted)' }}>
            <Timer size={14} style={{ verticalAlign: 'middle', marginRight: 4 }} />
            分析耗时 {rcaResult.analysisTime}
          </div>
          <button className="btn btn-primary btn-sm">重新分析</button>
        </div>
      </div>

      {/* Judgment Banner */}
      <div className="alert-banner orange" style={{ marginBottom: 20 }}>
        <div className="alert-title">
          判定：<strong>{rcaResult.judgment}</strong>
        </div>
        <div style={{ marginTop: 4 }}>{rcaResult.judgmentDetail}</div>
      </div>

      {/* Hypotheses */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-title">候选根因</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {rcaResult.hypotheses.map(h => (
            <div key={h.rank} className="hypothesis-card">
              <div className={'hypothesis-rank rank-' + h.rank}>{h.rank}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, marginBottom: 4 }}>{h.name}</div>
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                  得分 {h.score} · {h.confidence}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                {h.tags.map((t, i) => (
                  <span key={i} className={'badge badge-' + h.tagColors[i]}>{t}</span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Explanation Flow */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-title">解释路径</div>
        {rcaResult.explanations.map((e, i) => (
          <div key={i} className={'explanation-row ' + e.bg}>
            <span className={'badge badge-' + e.tagType}>{e.tag}</span>
            <span className="row-text">{e.text}</span>
          </div>
        ))}
      </div>

      {/* Evidence Overview */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileSearch size={16} />
          证据概览
        </div>
        <div style={{ fontSize: 14, color: 'var(--muted)', marginBottom: 12 }}>
          共收集 {rcaResult.evidenceOverview.total} 条证据
        </div>
        <div className="grid-5" style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          {Object.entries(rcaResult.evidenceOverview.sources).map(([src, count]) => (
            <div key={src} style={{ minWidth: 120 }}>
              <div style={{ fontSize: 12, color: 'var(--muted)' }}>{src}</div>
              <div style={{ fontSize: 22, fontWeight: 700, color: '#0b7285' }}>{count}</div>
            </div>
          ))}
        </div>
      </div>

      {/* AI Advisory */}
      <div className="advisory-banner">
        <div className="advisory-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Sparkles size={16} style={{ color: 'var(--blue)' }} />
          AI 建议
        </div>
        <div>{rcaResult.aiAdvisory.title}</div>
        <div style={{ marginTop: 8, fontSize: 13, color: 'var(--text)' }}>
          {rcaResult.aiAdvisory.detail}
        </div>
        <div className="advisory-boundary">{rcaResult.aiAdvisory.boundary}</div>
      </div>

      <div className="footer-note">* 所有数据为模拟数据，AI 建议仅供参考</div>
    </div>
  )
}
