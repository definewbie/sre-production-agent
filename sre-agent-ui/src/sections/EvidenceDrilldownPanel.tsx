import { useState } from 'react'
import { evidenceData } from '../data/mockData'

type TabId = 'top' | 'all'

// Badge color mapping for evidence type
const typeBadgeMap: Record<string, { bg: string; text: string; border: string }> = {
  'downstream_span_slow': { bg: '#e0efff', text: '#175cd3', border: '#84caff' },
  'timeout_error': { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  'latency_p95': { bg: '#fef0c7', text: '#b54708', border: '#fedf89' },
  'child_span_dominates': { bg: '#e0efff', text: '#175cd3', border: '#84caff' },
  'downstream_timeout': { bg: '#fee4e2', text: '#b42318', border: '#fda29b' },
  'metric_no_signal': { bg: '#ffffff', text: '#667085', border: '#d9e1ec' },
}

function TypeBadge({ type }: { type: string }) {
  const style = typeBadgeMap[type] || { bg: '#e0efff', text: '#175cd3', border: '#84caff' }
  return (
    <span style={{
      display: 'inline-block',
      padding: '2px 8px',
      borderRadius: 4,
      fontSize: 12,
      fontWeight: 600,
      background: style.bg,
      color: style.text,
      border: '1px solid ' + style.border,
    }}>
      {type}
    </span>
  )
}

function StrengthBadge({ strength }: { strength: string }) {
  if (strength === 'strong') {
    return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600, background: '#dcfae6', color: '#027a48', border: '1px solid #75e0a7' }}>strong</span>
  }
  if (strength === 'moderate') {
    return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600, background: '#fef0c7', color: '#b54708', border: '1px solid #fedf89' }}>moderate</span>
  }
  return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, fontSize: 12, color: '#667085', background: '#fff', border: '1px solid #d9e1ec' }}>—</span>
}

export default function EvidenceDrilldownPanel() {
  const [activeTab, setActiveTab] = useState<TabId>('top')

  // Determine noSignal counts per source
  const sourceCards = evidenceData.sources.map(s => ({
    name: s.name,
    count: s.count,
    noSignal: s.noSignal || 0,
    isAlert: s.name === 'Alertmanager',
  }))

  // Build evidence rows matching SVG
  const evidenceRows = evidenceData.topEvidence.map(e => ({
    time: e.time,
    source: e.source,
    type: e.type,
    summary: e.summary,
    strength: e.strength,
    isNoSignal: e.strength === 'none',
  }))

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        证据明细 ＞ 所有证据
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">4 证据明细（Evidence Drill-down）</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button className="btn btn-ghost btn-sm" style={{ fontSize: 13 }}>
            时间范围：最近 15 分钟
          </button>
          <button className="btn btn-ghost btn-sm" style={{ fontSize: 16, padding: '6px 10px' }}>⟳</button>
          <button className="btn btn-ghost btn-sm" style={{ fontSize: 16, padding: '6px 10px' }}>⤓</button>
        </div>
      </div>

      {/* Source Summary Cards */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        {sourceCards.map(s => (
          <div key={s.name} className="card" style={{ flex: 1, padding: '16px 20px' }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)', marginBottom: 12 }}>{s.name}</div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <span style={{ fontSize: 32, fontWeight: 700, color: s.isAlert ? 'var(--red)' : '#0b7285' }}>
                {s.count}
              </span>
              <span style={{
                fontSize: 12,
                color: s.noSignal > 0 ? 'var(--red)' : 'var(--green)',
                fontWeight: 600,
              }}>
                {s.noSignal > 0 ? s.noSignal + ' 无信号' : '0 无信号'}
              </span>
            </div>
          </div>
        ))}
      </div>

      {/* Tab Bar */}
      <div style={{
        display: 'flex',
        gap: 24,
        borderBottom: '2px solid var(--line)',
        marginBottom: 16,
      }}>
        {[
          { id: 'top' as TabId, label: 'Top 证据' },
          { id: 'all' as TabId, label: '所有证据' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{
              background: 'none',
              border: 'none',
              padding: '8px 0',
              cursor: 'pointer',
              fontSize: 14,
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

      {/* Evidence Table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th style={{ width: 90 }}>时间</th>
              <th style={{ width: 100 }}>来源</th>
              <th style={{ width: 170 }}>类型</th>
              <th>内容摘要</th>
              <th style={{ width: 80, textAlign: 'center' }}>强度</th>
            </tr>
          </thead>
          <tbody>
            {evidenceRows.map((e, i) => (
              <tr key={i} style={e.isNoSignal ? { opacity: 0.55 } : undefined}>
                <td style={{ fontFamily: 'monospace', fontSize: 13, whiteSpace: 'nowrap' }}>{e.time}</td>
                <td style={{ fontSize: 13 }}>{e.source}</td>
                <td><TypeBadge type={e.type} /></td>
                <td style={{ fontSize: 13 }}>{e.summary}</td>
                <td style={{ textAlign: 'center' }}><StrengthBadge strength={e.strength} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Footer */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginTop: 12,
        marginBottom: 8,
      }}>
        <span style={{ fontSize: 13, color: 'var(--muted)' }}>
          共 {evidenceData.total} 条证据
        </span>
        <button className="btn btn-ghost btn-sm">导出证据</button>
      </div>

      <div className="footer-note">* 证据为模拟数据</div>
    </div>
  )
}
