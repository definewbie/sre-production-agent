import { environmentComponents } from '../data/mockData'

const components = [
  { name: 'Prometheus', status: 'normal', endpoint: 'http://localhost:9090', responseTime: '120ms', lastCheck: '14:30:25', action: '打开' },
  { name: 'Loki', status: 'normal', endpoint: 'http://localhost:3100', responseTime: '95ms', lastCheck: '14:30:25', action: '打开' },
  { name: 'Jaeger', status: 'normal', endpoint: 'http://localhost:16686', responseTime: '180ms', lastCheck: '14:30:25', action: '打开' },
  { name: 'Kubernetes', status: 'normal', endpoint: 'kind/sre-agent', responseTime: '60ms', lastCheck: '15 pods running', action: '查看' },
  { name: 'Demo Services', status: 'normal', endpoint: 'demo namespace', responseTime: '3/3 ready', lastCheck: '14:30:25', action: '查看' },
  { name: 'Alertmanager', status: 'normal', endpoint: 'http://localhost:9093', responseTime: '45ms', lastCheck: '14:30:25', action: '打开' },
]

const normalCount = components.filter(c => c.status === 'normal').length
const totalCount = components.length

export default function EnvironmentStatusPanel() {
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
          <button className="btn btn-ghost btn-sm">刷新</button>
          <button className="btn btn-primary btn-sm">运行健康检查</button>
        </div>
      </div>

      {/* KPI Summary Cards */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>组件总数</div>
          <div style={{ fontSize: 32, fontWeight: 700 }}>{totalCount}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>正常组件</div>
          <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--green)' }}>{normalCount}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>异常组件</div>
          <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--red)' }}>{totalCount - normalCount}</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>最近检查</div>
          <div style={{ fontSize: 20, fontWeight: 700 }}>14:30:25</div>
        </div>
        <div className="card" style={{ flex: 1, padding: '16px 20px' }}>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 8 }}>环境状态</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--green)' }}>Ready</div>
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
              <tr key={i}>
                <td style={{ fontWeight: 600 }}>{c.name}</td>
                <td>
                  <span className="badge badge-green">正常</span>
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{c.endpoint}</td>
                <td>{c.responseTime}</td>
                <td>{c.lastCheck}</td>
                <td style={{ textAlign: 'center' }}>
                  <button className="btn btn-ghost btn-sm" style={{ padding: '2px 8px', fontSize: 13 }}>
                    {c.action}
                  </button>
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
            <button className="btn btn-primary btn-sm">刷新状态</button>
            <button className="btn btn-ghost btn-sm">打开 Grafana</button>
            <button className="btn btn-ghost btn-sm">查看 K8s Pods</button>
          </div>
        </div>
      </div>
    </div>
  )
}
