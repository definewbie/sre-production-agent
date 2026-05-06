import { evidenceData } from '../data/mockData'
import { FileSearch, Filter } from 'lucide-react'

export default function EvidenceDrilldownPanel() {
  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">证据明细</h1>
          <div className="page-subtitle">
            order-service 异常 · 共 {evidenceData.total} 条证据
          </div>
        </div>
        <div className="flex items-center gap-12">
          <button className="btn btn-ghost btn-sm">
            <Filter size={14} />
            筛选
          </button>
        </div>
      </div>

      {/* Source Summary Cards */}
      <div className="source-cards">
        {evidenceData.sources.map(s => (
          <div key={s.name} className="source-card">
            <div className="source-name">{s.name}</div>
            <div className={'source-count' + (s.noSignal > 0 ? ' red' : '')}>{s.count}</div>
            {s.noSignal > 0 ? (
              <div className="source-signal red">无信号 {s.noSignal}</div>
            ) : (
              <div className="source-signal green">全部有效</div>
            )}
          </div>
        ))}
      </div>

      {/* Tab Bar */}
      <div className="tab-bar">
        <div className="tab-item active">全部证据</div>
        <div className="tab-item">强信号</div>
        <div className="tab-item">无信号</div>
      </div>

      {/* Evidence Table */}
      <div className="card">
        <table className="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>来源</th>
              <th>类型</th>
              <th>摘要</th>
              <th>信号强度</th>
            </tr>
          </thead>
          <tbody>
            {evidenceData.topEvidence.map((e, i) => (
              <tr key={i}>
                <td style={{ fontFamily: 'monospace', fontSize: 13, whiteSpace: 'nowrap' }}>{e.time}</td>
                <td><span className="badge badge-blue">{e.source}</span></td>
                <td style={{ fontSize: 13 }}>{e.type}</td>
                <td>{e.summary}</td>
                <td>
                  {e.strength === 'strong' && <span className="badge badge-green">强</span>}
                  {e.strength === 'moderate' && <span className="badge badge-orange">中</span>}
                  {e.strength === 'none' && <span className="badge badge-red">无信号</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="footer-note" style={{ marginTop: 16 }}>
        * 证据为模拟数据，实际场景从 Prometheus / Loki / Jaeger / Kubernetes / Alertmanager 采集
      </div>
    </div>
  )
}
