import { useState, useEffect } from 'react'
import { incidentSummary } from '../data/mockData'
import { services as mockServices } from '../data/mockData'
import { getServiceHealthSummary, type ServiceHealthView } from '../api/client'

type TabId = 'metrics' | 'callchain' | 'resources' | 'timeline'

interface Props {
  serviceName: string | null
  onBack: () => void
  onRca: () => void
}

// Sparkline SVG for metric cards
function MetricSparkline({ color, data, width = 150, height = 50 }: {
  color: string
  data: number[]
  width?: number
  height?: number
}) {
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  const stepX = width / (data.length - 1)
  const pathD = data.map((v, i) => {
    const x = i * stepX
    const y = height - ((v - min) / range) * (height - 10) - 5
    return (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1)
  }).join(' ')

  return (
    <svg width={width} height={height} style={{ marginTop: 8 }}>
      <path d={pathD} fill="none" stroke={color} strokeWidth="2" />
    </svg>
  )
}

const tabs: { id: TabId; label: string }[] = [
  { id: 'metrics', label: '关键指标' },
  { id: 'callchain', label: '调用链路' },
  { id: 'resources', label: '资源状态' },
  { id: 'timeline', label: '事件时间线' },
]

export default function IncidentDetailPanel({ serviceName, onBack, onRca }: Props) {
  const svc = serviceName || incidentSummary.service
  const [activeTab, setActiveTab] = useState<TabId>('metrics')
  const [realServices, setRealServices] = useState<ServiceHealthView[]>([])
  const [loading, setLoading] = useState(true)
  const [dataSource, setDataSource] = useState<'real' | 'mock'>('mock')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getServiceHealthSummary().then(res => {
      if (cancelled) return
      if (res.data && res.data.services.length > 0) {
        setRealServices(res.data.services)
        setDataSource('real')
      }
      setLoading(false)
    }).catch(() => {
      if (!cancelled) setLoading(false)
    })
    return () => { cancelled = true }
  }, [])

  // 优先用真实 API 数据，fallback 到 mock
  const allServices = realServices.length > 0 ? realServices : mockServices.map(m => ({
    name: m.name,
    status: (m.status === 'abnormal' ? 'degraded' : 'healthy') as ServiceHealthView['status'],
    reachable: true,
    url: '',
    health: '',
    errorRate: m.errorRate,
    errorRateTrend: m.errorRateTrend,
    errorRateDirection: m.errorRateDirection as 'up' | 'down' | undefined,
    p95Latency: m.p95Latency,
    p95Trend: m.p95Trend,
    p95Direction: m.p95Direction as 'up' | 'down' | undefined,
    rps: m.rps,
    saturation: m.saturation,
    restarts: m.restarts,
    faultEnabled: false,
    faultType: 'normal',
    message: '',
    source: 'mock' as const,
  }))

  const svcData = allServices.find(s => s.name === svc)
  const currentSvc = svcData || allServices[0]

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 8 }}>
        <span onClick={onBack} style={{ cursor: 'pointer', color: 'var(--blue)' }}>服务健康</span>
        <span style={{ margin: '0 6px' }}>＞</span>
        <span>异常详情</span>
      </div>

      {/* Time Range */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 4 }}>
        <span style={{ fontSize: 13, color: 'var(--muted)' }}>时间范围：最近 15 分钟</span>
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <h1 className="page-title red" style={{ fontSize: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 14 }}>●</span>
            {svc} 异常详情
          </h1>
          <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
            <span>状态：{currentSvc.status === 'healthy' ? '正常' : currentSvc.status === 'degraded' ? '异常（降级）' : currentSvc.status === 'down' ? '不可达' : '未知'}</span>
            <span style={{ margin: '0 12px' }}>|</span>
            <span>可达：{currentSvc.reachable ? '是' : '否'}</span>
            {currentSvc.faultEnabled && <>
              <span style={{ margin: '0 12px' }}>|</span>
              <span style={{ color: 'var(--red)' }}>故障注入：{currentSvc.faultType}</span>
            </>}
            <span style={{ margin: '0 12px' }}>|</span>
            <span>数据来源：{dataSource === 'real' ? '✅ 实时 API' : '⚠️ Mock 数据'}</span>
          </div>
        </div>
        <button className="btn btn-primary" onClick={onRca}>
          进行 RCA 分析
        </button>
      </div>

      {/* Anomaly Summary Banner */}
      <div style={{
        background: currentSvc.status === 'healthy' ? '#f0fdf4' : '#fff1f3',
        border: '1px solid ' + (currentSvc.status === 'healthy' ? 'var(--badge-green-border, #b7eb8f)' : 'var(--badge-red-border)'),
        borderRadius: 8,
        padding: '16px 20px',
        marginBottom: 20,
      }}>
        <div style={{ fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>
          {currentSvc.status === 'healthy'
            ? '当前状态正常'
            : '当前异常：' + [
                currentSvc.errorRate && currentSvc.errorRate !== '0%' ? '错误率 ' + currentSvc.errorRate : '',
                currentSvc.p95Latency ? 'P95延迟 ' + currentSvc.p95Latency : '',
                currentSvc.faultEnabled ? '故障注入 ' + currentSvc.faultType : '',
                currentSvc.message || '',
              ].filter(Boolean).join('、')
          }
        </div>
        <div style={{ color: 'var(--text)' }}>
          {'服务 ' + currentSvc.name + ' — ' + (currentSvc.reachable ? '可达' : '不可达') + '，健康检查：' + (currentSvc.health || '无数据')}
        </div>
      </div>

      {/* Tabs */}
      <div style={{
        display: 'flex',
        gap: 24,
        borderBottom: '2px solid var(--line)',
        marginBottom: 20,
        paddingBottom: 0,
      }}>
        {tabs.map(tab => (
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

      {/* Tab Content */}
      {activeTab === 'metrics' && (
        <>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 40, color: 'var(--muted)' }}>
              加载服务指标中...
            </div>
          ) : (
          <>
          {/* 4 Metric Cards */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
            <div className="card" style={{ flex: 1, padding: 20 }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>错误率 (5m)</div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginTop: 8 }}>
                <span style={{ fontSize: 32, fontWeight: 700 }}>{currentSvc.errorRate || 'N/A'}</span>
                {currentSvc.errorRateTrend && (
                  <span style={{ fontSize: 12, color: 'var(--red)', fontWeight: 600 }}>
                    {currentSvc.errorRateDirection === 'up' ? '↑' : '↓'}{currentSvc.errorRateTrend}
                  </span>
                )}
              </div>
              <MetricSparkline color="#f04438" data={[2.0, 2.5, 2.2, 3.0, 3.8, 4.2, parseFloat((currentSvc.errorRate || '0').replace('%', '')) || 4.7]} />
            </div>
            <div className="card" style={{ flex: 1, padding: 20 }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>P95 延迟 (5m)</div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginTop: 8 }}>
                <span style={{ fontSize: 32, fontWeight: 700 }}>{currentSvc.p95Latency || 'N/A'}</span>
                {currentSvc.p95Trend && (
                  <span style={{ fontSize: 12, color: 'var(--red)', fontWeight: 600 }}>
                    {currentSvc.p95Direction === 'up' ? '↑' : '↓'}{currentSvc.p95Trend}
                  </span>
                )}
              </div>
              <MetricSparkline color="#f04438" data={[0.5, 0.6, 0.8, 1.0, 1.2, 1.5, parseFloat((currentSvc.p95Latency || '0').replace('s', '')) || 1.85]} />
            </div>
            <div className="card" style={{ flex: 1, padding: 20 }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>流量 (rps)</div>
              <div style={{ marginTop: 8 }}>
                <span style={{ fontSize: 32, fontWeight: 700 }}>{currentSvc.rps != null ? currentSvc.rps : 'N/A'}</span>
              </div>
              <MetricSparkline color="#2e90fa" data={[1.8, 2.0, 1.9, 2.1, 2.3, 2.0, currentSvc.rps || 2.1]} />
            </div>
            <div className="card" style={{ flex: 1, padding: 20 }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--title)' }}>饱和度</div>
              <div style={{ marginTop: 8 }}>
                <span style={{ fontSize: 32, fontWeight: 700 }}>{currentSvc.saturation != null ? currentSvc.saturation + '%' : 'N/A'}</span>
              </div>
              <MetricSparkline color="#12b76a" data={[30, 35, 38, 40, 42, 44, currentSvc.saturation || 45]} />
            </div>
          </div>

          {/* Bottom Row: Anomaly Summary + Endpoints */}
          <div style={{ display: 'flex', gap: 20 }}>
            <div className="card" style={{ flex: 1, padding: 20 }}>
              <div className="card-title">异常摘要</div>
              <div style={{ marginTop: 12 }}>
                {incidentSummary.details.map((d, i) => (
                  <div key={i} style={{
                    padding: '8px 0',
                    fontSize: 14,
                    color: i < 3 ? 'var(--red)' : 'var(--text)',
                  }}>
                    • {d}
                  </div>
                ))}
              </div>
            </div>
            <div className="card" style={{ flex: 1, padding: 20 }}>
              <div className="card-title">受影响的端点 (Top)</div>
              <table className="data-table" style={{ marginTop: 8 }}>
                <thead>
                  <tr>
                    <th>端点</th>
                    <th>错误率</th>
                    <th>P95 延迟</th>
                    <th>请求数</th>
                  </tr>
                </thead>
                <tbody>
                  {incidentSummary.endpoints.map((ep, i) => (
                    <tr key={i}>
                      <td style={{ fontWeight: 600 }}>{ep.path}</td>
                      <td className="red">{ep.errorRate}</td>
                      <td className="red">{ep.p95}</td>
                      <td>{ep.requests}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          </>)}
        </>
      )}

      {activeTab === 'callchain' && (
        <div className="card" style={{ padding: 24 }}>
          <div className="card-title">调用链路</div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '32px 0', gap: 0 }}>
            <div className="topo-node red" style={{ cursor: 'default' }}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>order-service</div>
              <div style={{ marginTop: 6 }}><span className="badge badge-red">异常</span></div>
              <div style={{ fontSize: 12, color: 'var(--red)', marginTop: 4 }}>错误率 4.7%</div>
            </div>
            <div style={{ padding: '0 16px' }}>
              <svg width="60" height="20">
                <line x1="0" y1="10" x2="48" y2="10" stroke="#f04438" strokeWidth="2" />
                <polygon points="48,5 58,10 48,15" fill="#f04438" />
              </svg>
            </div>
            <div className="topo-node red" style={{ cursor: 'default' }}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>payment-service</div>
              <div style={{ marginTop: 6 }}><span className="badge badge-red">异常</span></div>
              <div style={{ fontSize: 12, color: 'var(--red)', marginTop: 4 }}>延迟 2.42s</div>
            </div>
            <div style={{ padding: '0 16px' }}>
              <svg width="60" height="20">
                <line x1="0" y1="10" x2="48" y2="10" stroke="#f04438" strokeWidth="2" />
                <polygon points="48,5 58,10 48,15" fill="#f04438" />
              </svg>
            </div>
            <div className="topo-node green" style={{ cursor: 'default' }}>
              <div style={{ fontWeight: 700, fontSize: 15 }}>inventory-service</div>
              <div style={{ marginTop: 6 }}><span className="badge badge-green">正常</span></div>
              <div style={{ fontSize: 12, color: 'var(--green)', marginTop: 4 }}>延迟 0.38s</div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'resources' && (
        <div className="card" style={{ padding: 20 }}>
          <div className="card-title">资源状态</div>
          <table className="data-table" style={{ marginTop: 12 }}>
            <thead>
              <tr>
                <th>资源类型</th>
                <th>当前值</th>
                <th>阈值</th>
                <th>状态</th>
                <th>趋势</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ fontWeight: 600 }}>CPU 使用率</td>
                <td>62%</td>
                <td>80%</td>
                <td><span className="badge badge-green">正常</span></td>
                <td className="green">稳定</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>内存使用率</td>
                <td>71%</td>
                <td>85%</td>
                <td><span className="badge badge-green">正常</span></td>
                <td className="green">稳定</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>Pod 重启次数</td>
                <td>0</td>
                <td>3</td>
                <td><span className="badge badge-green">正常</span></td>
                <td className="green">无重启</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>连接池使用率</td>
                <td>88%</td>
                <td>80%</td>
                <td><span className="badge badge-red">异常</span></td>
                <td className="red">↑ 上升</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {activeTab === 'timeline' && (
        <div className="card" style={{ padding: 20 }}>
          <div className="card-title">事件时间线</div>
          <div style={{ marginTop: 16 }}>
            {[
              { time: '14:20:00', event: '错误率开始上升', type: 'warning' },
              { time: '14:21:15', event: 'P95 延迟开始显著升高', type: 'warning' },
              { time: '14:24:30', event: 'Alertmanager 触发 P1 告警：order-service 高错误率', type: 'alert' },
              { time: '14:24:58', event: '异常检测：payment-service 高延迟', type: 'alert' },
              { time: '14:25:10', event: 'Alertmanager 触发 P2 告警：payment-service 高延迟', type: 'alert' },
              { time: '14:30:00', event: 'SRE Agent 开始 RCA 分析', type: 'info' },
            ].map((item, i) => (
              <div key={i} style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 16,
                padding: '12px 0',
                borderTop: i > 0 ? '1px solid var(--line)' : 'none',
              }}>
                <span style={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: item.type === 'alert' ? 'var(--red)' : item.type === 'warning' ? 'var(--orange)' : 'var(--blue)',
                  flexShrink: 0,
                  marginTop: 5,
                }} />
                <span style={{ fontSize: 13, color: 'var(--muted)', whiteSpace: 'nowrap', minWidth: 70 }}>
                  {item.time}
                </span>
                <span style={{ fontSize: 14, color: 'var(--text)' }}>{item.event}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="footer-note">
        * 指标数据来源：{dataSource === 'real' ? '实时 API' : 'Mock 数据（API 不可用时）'}；
        调用链路 / 资源状态 / 事件时间线暂为模拟数据
      </div>
    </div>
  )
}
