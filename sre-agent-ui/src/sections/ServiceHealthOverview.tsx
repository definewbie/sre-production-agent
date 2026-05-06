import { services, alerts } from '../data/mockData'
import { ChevronDown, RefreshCw } from 'lucide-react'

interface Props {
  onServiceClick: (name: string) => void
}

// Mini sparkline SVG component
function Sparkline({ color, width = 60, height = 20 }: { color: string; width?: number; height?: number }) {
  // Generate a pseudo-random zigzag path
  const pts = [0.6, 0.35, 0.55, 0.25, 0.5, 0.3]
  const stepX = width / (pts.length - 1)
  const pathD = pts.map((p, i) => {
    const x = i * stepX
    const y = p * height
    return (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1)
  }).join(' ')

  return (
    <svg width={width} height={height} style={{ verticalAlign: 'middle', marginLeft: 4 }}>
      <path d={pathD} fill="none" stroke={color} strokeWidth="2" />
    </svg>
  )
}

export default function ServiceHealthOverview({ onServiceClick }: Props) {
  const abnormalCount = services.filter(s => s.status === 'abnormal').length
  const normalCount = services.filter(s => s.status === 'normal').length
  const alertCount = alerts.length

  return (
    <div>
      {/* Page Title + Time Controls */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">服务健康总览</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <button className="btn btn-ghost btn-sm" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            最近 5 分钟
            <ChevronDown size={14} />
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--muted)' }}>
            自动刷新：
            <div className="toggle active" style={{ transform: 'scale(0.8)' }}>
              <div className="toggle-knob" />
            </div>
          </div>
          <span style={{ fontSize: 13, color: 'var(--muted)' }}>更新时间：14:30:25</span>
        </div>
      </div>

      {/* KPI Cards Row — 5 cards per SVG */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
        <div className="kpi-card">
          <div className="kpi-label">服务总数</div>
          <div className="kpi-value">{services.length}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">异常服务</div>
          <div className="kpi-value red">{abnormalCount}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">健康服务</div>
          <div className="kpi-value green">{normalCount}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">告警数</div>
          <div className="kpi-value orange">{alertCount}</div>
        </div>
        <div className="kpi-card" style={{ minWidth: 200 }}>
          <div className="kpi-label">影响用户</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
            <div className="kpi-value">128</div>
            <span style={{ fontSize: 12, color: 'var(--red)', fontWeight: 600 }}>↑ 12%</span>
          </div>
        </div>
      </div>

      {/* Service Health Table */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-title">关键服务健康状态</div>
        <table className="data-table">
          <thead>
            <tr>
              <th>服务名称</th>
              <th>状态</th>
              <th>错误率 (5m)</th>
              <th>P95 延迟 (5m)</th>
              <th>流量 (rps)</th>
              <th>饱和度</th>
              <th>最近重启</th>
            </tr>
          </thead>
          <tbody>
            {services.map(s => (
              <tr key={s.name} style={{ cursor: 'pointer' }} onClick={() => onServiceClick(s.name)}>
                <td style={{ fontWeight: 600 }}>{s.name}</td>
                <td>
                  <span className={'badge ' + (s.status === 'abnormal' ? 'badge-red' : 'badge-green')}>
                    {s.status === 'abnormal' ? '异常' : '正常'}
                  </span>
                </td>
                <td>
                  <span className={s.status === 'abnormal' ? 'red' : 'green'}>
                    {s.errorRate}
                  </span>
                  <span className={'kpi-trend ' + s.errorRateDirection}>
                    {s.errorRateDirection === 'up' ? '↑' : '↓'}{s.errorRateTrend}
                  </span>
                </td>
                <td>
                  <span className={s.status === 'abnormal' ? 'red' : 'green'}>
                    {s.p95Latency}
                  </span>
                  <span className={'kpi-trend ' + s.p95Direction}>
                    {s.p95Direction === 'up' ? '↑' : '↓'}{s.p95Trend}
                  </span>
                </td>
                <td>
                  {s.rps}
                  <Sparkline color={s.status === 'abnormal' ? '#2e90fa' : '#12b76a'} />
                </td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span>{s.saturation}%</span>
                    <div className="saturation-bar">
                      <div
                        className="saturation-fill"
                        style={{
                          width: s.saturation + '%',
                          background: s.saturation > 70 ? 'var(--red)' : 'var(--green)',
                        }}
                      />
                    </div>
                  </div>
                </td>
                <td>{s.restarts}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Bottom Row: Topology + Alerts */}
      <div style={{ display: 'flex', gap: 20 }}>
        {/* Service Dependency Topology */}
        <div className="card" style={{ flex: '1.5' }}>
          <div className="card-title">服务依赖拓扑（当前影响路径）</div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px 0', gap: 0 }}>
            {/* order-service */}
            <div className="topo-node green" style={{ cursor: 'pointer' }} onClick={() => onServiceClick('order-service')}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>order-service</div>
              <div style={{ marginTop: 6 }}><span className="badge badge-red">异常</span></div>
              <div style={{ fontSize: 12, color: 'var(--red)', marginTop: 4 }}>错误率 4.7%</div>
            </div>

            {/* Arrow */}
            <div style={{ padding: '0 12px' }}>
              <svg width="60" height="20">
                <line x1="0" y1="10" x2="48" y2="10" stroke="#f04438" strokeWidth="2" />
                <polygon points="48,5 58,10 48,15" fill="#f04438" />
              </svg>
            </div>

            {/* payment-service */}
            <div className="topo-node red" style={{ cursor: 'pointer' }} onClick={() => onServiceClick('payment-service')}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>payment-service</div>
              <div style={{ marginTop: 6 }}><span className="badge badge-red">异常</span></div>
              <div style={{ fontSize: 12, color: 'var(--red)', marginTop: 4 }}>延迟 2.42s</div>
            </div>

            {/* Arrow */}
            <div style={{ padding: '0 12px' }}>
              <svg width="60" height="20">
                <line x1="0" y1="10" x2="48" y2="10" stroke="#f04438" strokeWidth="2" />
                <polygon points="48,5 58,10 48,15" fill="#f04438" />
              </svg>
            </div>

            {/* inventory-service */}
            <div className="topo-node green" style={{ cursor: 'pointer' }} onClick={() => onServiceClick('inventory-service')}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>inventory-service</div>
              <div style={{ marginTop: 6 }}><span className="badge badge-green">正常</span></div>
              <div style={{ fontSize: 12, color: 'var(--green)', marginTop: 4 }}>延迟 0.38s</div>
            </div>
          </div>
        </div>

        {/* Recent Alerts */}
        <div className="card" style={{ flex: '1' }}>
          <div className="card-title">最近告警</div>
          <div>
            {alerts.map((a, i) => (
              <div key={i} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '16px 0',
                borderTop: i > 0 ? '1px solid var(--line)' : 'none',
              }}>
                <span className="status-dot" style={{
                  background: a.level === 'P1' ? 'var(--red)' : 'var(--orange)',
                  flexShrink: 0,
                  width: 10,
                  height: 10,
                }} />
                <span style={{ flex: 1, fontSize: 14, color: 'var(--text)' }}>
                  {a.service} {a.message}
                </span>
                <span className={'badge ' + (a.level === 'P1' ? 'badge-red' : 'badge-orange')}>
                  {a.level}
                </span>
                <span style={{ fontSize: 13, color: 'var(--muted)', whiteSpace: 'nowrap' }}>{a.time}</span>
              </div>
            ))}
            <div style={{ marginTop: 16, textAlign: 'right' }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--blue)', cursor: 'pointer' }}>
                查看全部告警 →
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="footer-note">* 所有数据为模拟数据，API 接入后将替换</div>
    </div>
  )
}
