import { environmentComponents } from '../data/mockData'
import { Monitor, ExternalLink, CheckCircle, RefreshCw } from 'lucide-react'

export default function EnvironmentStatusPanel() {
  const normalCount = environmentComponents.filter(c => c.status === 'normal').length

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">环境状态</h1>
          <div className="page-subtitle">
            local-kind-demo · {normalCount}/{environmentComponents.length} 组件正常
          </div>
        </div>
        <button className="btn btn-ghost btn-sm">
          <RefreshCw size={14} />
          刷新
        </button>
      </div>

      {/* Summary */}
      <div className="kpi-row">
        <div className="kpi-card">
          <div className="kpi-label">环境</div>
          <div className="kpi-value" style={{ fontSize: 18 }}>local-kind-demo</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">组件状态</div>
          <div className="kpi-value green">{normalCount}/{environmentComponents.length}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">最近检查</div>
          <div className="kpi-value" style={{ fontSize: 18 }}>14:30:25</div>
        </div>
      </div>

      {/* Component Table */}
      <div className="card">
        <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Monitor size={16} />
          组件详情
        </div>
        <table className="data-table">
          <thead>
            <tr>
              <th>组件</th>
              <th>状态</th>
              <th>端点 / 详情</th>
              <th>响应时间</th>
              <th>最近检查</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {environmentComponents.map(c => (
              <tr key={c.name}>
                <td style={{ fontWeight: 600 }}>{c.name}</td>
                <td>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <CheckCircle size={14} style={{ color: 'var(--green)' }} />
                    <span className="badge badge-green">正常</span>
                  </span>
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{c.endpoint}</td>
                <td>{c.responseTime}</td>
                <td>{c.lastCheck}</td>
                <td>
                  <button className="btn btn-ghost btn-sm" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <ExternalLink size={12} />
                    {c.action}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="footer-note">* 状态为模拟数据</div>
    </div>
  )
}
