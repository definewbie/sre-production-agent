import { useState } from 'react'

interface ExperimentLog {
  time: string
  message: string
  type: 'info' | 'success' | 'error'
}

const TARGET_SERVICES = ['order-service', 'payment-service', 'inventory-service']
const FAULT_TYPES = ['延迟 (Latency)', 'Pod Kill', 'CPU 压力', '内存泄漏']

function SelectField({ label, value, options, onChange }: {
  label: string
  value: string
  options: string[]
  onChange: (v: string) => void
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
      <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0 }}>{label}</label>
      <select value={value} onChange={e => onChange(e.target.value)} style={{
        flex: 1,
        height: 34,
        border: '1px solid #c8d3e1',
        borderRadius: 8,
        padding: '0 12px',
        fontSize: 14,
        color: 'var(--text)',
        background: '#fff',
        outline: 'none',
      }}>
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  )
}

function InputField({ label, value, onChange, unit }: {
  label: string
  value: string
  onChange: (v: string) => void
  unit?: string
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
      <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0 }}>{label}</label>
      <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
        <input
          type="text"
          value={value}
          onChange={e => onChange(e.target.value)}
          style={{
            height: 34,
            border: '1px solid #c8d3e1',
            borderRadius: 8,
            padding: '0 12px',
            fontSize: 14,
            color: 'var(--text)',
            width: 120,
            outline: 'none',
          }}
        />
        {unit && <span style={{ marginLeft: 8, fontSize: 13, color: 'var(--muted)' }}>{unit}</span>}
      </div>
    </div>
  )
}

export default function ChaosExperimentPanel() {
  const [target, setTarget] = useState('payment-service')
  const [faultType, setFaultType] = useState('延迟 (Latency)')
  const [latency, setLatency] = useState('1500')
  const [errorRate, setErrorRate] = useState('0')
  const [duration, setDuration] = useState('5')
  const [trafficTarget, setTrafficTarget] = useState('order-service /checkout')
  const [rps, setRps] = useState('2')
  const [observeWindow, setObserveWindow] = useState('5')
  const [expName, setExpName] = useState('payment-latency-test-001')
  const [expDesc, setExpDesc] = useState('模拟 payment-service 延迟 1500ms')

  const [running, setRunning] = useState(false)
  const [logs, setLogs] = useState<ExperimentLog[]>([])
  const [drawerOpen, setDrawerOpen] = useState(false)

  const addLog = (message: string, type: ExperimentLog['type'] = 'info') => {
    const now = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    setLogs(prev => [...prev, { time: now, message, type }])
  }

  const handleRun = () => {
    setRunning(true)
    setDrawerOpen(true)
    setLogs([])
    addLog('实验参数验证通过', 'info')
    setTimeout(() => addLog('连接 Kubernetes chaos namespace...', 'info'), 500)
    setTimeout(() => addLog('Chaos 资源已创建: ' + faultType, 'success'), 1200)
    setTimeout(() => addLog('目标服务: ' + target + '，持续时间: ' + duration + '分钟', 'info'), 1800)
    setTimeout(() => {
      addLog('Chaos 实验已启动', 'success')
      setRunning(false)
    }, 2500)
  }

  const handleStop = () => {
    addLog('正在停止实验...', 'info')
    setTimeout(() => {
      addLog('实验已停止，恢复中...', 'success')
    }, 800)
  }

  const handleRecover = () => {
    addLog('环境恢复中...', 'info')
    setTimeout(() => {
      addLog('环境已恢复正常', 'success')
    }, 600)
  }

  return (
    <div style={{ position: 'relative' }}>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        Chaos 实验 ＞ 创建实验
      </div>

      {/* Page Header */}
      <h1 className="page-title" style={{ marginBottom: 20 }}>5 Chaos 实验配置</h1>

      {/* Main Layout: Left form + Right preview */}
      <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
        {/* Left: Config Form */}
        <div className="card" style={{ flex: 2, padding: 24 }}>
          <div className="card-title" style={{ marginBottom: 20 }}>故障注入配置</div>

          <SelectField label="目标服务" value={target} options={TARGET_SERVICES} onChange={setTarget} />
          <SelectField label="故障类型" value={faultType} options={FAULT_TYPES} onChange={setFaultType} />
          <InputField label="延迟强度" value={latency} onChange={setLatency} unit="ms" />
          <InputField label="错误率" value={errorRate} onChange={setErrorRate} unit="%" />
          <InputField label="持续时间" value={duration} onChange={setDuration} unit="分钟" />
          <SelectField
            label="流量目标"
            value={trafficTarget}
            options={['order-service /checkout', 'order-service /orders', 'payment-service /pay']}
            onChange={setTrafficTarget}
          />
          <InputField label="RPS" value={rps} onChange={setRps} />
          <InputField label="观测窗口" value={observeWindow} onChange={setObserveWindow} unit="分钟" />

          <div style={{ display: 'flex', alignItems: 'flex-start', marginBottom: 20 }}>
            <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0, paddingTop: 8 }}>实验名称</label>
            <input
              type="text"
              value={expName}
              onChange={e => setExpName(e.target.value)}
              style={{
                height: 34,
                border: '1px solid #c8d3e1',
                borderRadius: 8,
                padding: '0 12px',
                fontSize: 14,
                color: 'var(--text)',
                width: 300,
                outline: 'none',
              }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'flex-start', marginBottom: 8 }}>
            <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0, paddingTop: 8 }}>实验描述</label>
            <textarea
              value={expDesc}
              onChange={e => setExpDesc(e.target.value)}
              style={{
                border: '1px solid #c8d3e1',
                borderRadius: 8,
                padding: '8px 12px',
                fontSize: 14,
                color: 'var(--text)',
                width: 300,
                height: 54,
                outline: 'none',
                resize: 'none',
                fontFamily: 'inherit',
              }}
            />
          </div>
        </div>

        {/* Right Column */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Preview Card */}
          <div className="card" style={{ padding: 20 }}>
            <div className="card-title" style={{ marginBottom: 16 }}>实验预览</div>
            {[
              { label: '目标服务', value: target },
              { label: '故障类型', value: faultType },
              { label: '延迟强度', value: latency + ' ms' },
              { label: '持续时间', value: duration + ' 分钟' },
              { label: '流量目标', value: trafficTarget },
              { label: 'RPS', value: rps },
            ].map((row, i) => (
              <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderTop: i > 0 ? '1px solid var(--line)' : 'none' }}>
                <span style={{ fontSize: 14, color: 'var(--text)' }}>{row.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--title)' }}>{row.value}</span>
              </div>
            ))}
          </div>

          {/* Safety Warning */}
          <div style={{
            background: '#fff7ed',
            border: '1px solid #fed7aa',
            borderRadius: 8,
            padding: 20,
          }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--orange)', marginBottom: 12 }}>
              安全提示
            </div>
            <div style={{ fontSize: 14, color: 'var(--text)', lineHeight: 1.8 }}>
              仅用于演示环境，请谨慎使用故障注入功能。
            </div>
            <div style={{ fontSize: 14, color: 'var(--text)', lineHeight: 1.8 }}>
              RCA 页面只消费实验结果，不直接控制故障。
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Actions */}
      <div style={{ display: 'flex', gap: 12, marginTop: 20, marginLeft: 0 }}>
        <button className="btn btn-ghost">保存配置</button>
        <button className="btn btn-primary" onClick={handleRun} disabled={running}>
          {running ? '启动中...' : '启动实验'}
        </button>
        <button style={{
          background: '#fff',
          border: '1px solid #fda29b',
          borderRadius: 8,
          padding: '0 16px',
          height: 38,
          fontSize: 14,
          fontWeight: 600,
          color: '#b42318',
          cursor: 'pointer',
        }} onClick={handleStop}>
          停止实验
        </button>
        <button className="btn btn-ghost" onClick={handleRecover}>恢复正常</button>
        <button className="btn btn-ghost" onClick={() => setDrawerOpen(true)} style={{ marginLeft: 'auto' }}>
          执行日志 ({logs.length})
        </button>
      </div>

      {/* Right Drawer: Execution Log */}
      {drawerOpen && (
        <>
          {/* Overlay */}
          <div
            onClick={() => setDrawerOpen(false)}
            style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(0,0,0,0.3)',
              zIndex: 1000,
            }}
          />
          {/* Drawer */}
          <div style={{
            position: 'fixed',
            top: 0,
            right: 0,
            width: 420,
            height: '100vh',
            background: '#fff',
            boxShadow: '-4px 0 24px rgba(0,0,0,0.12)',
            zIndex: 1001,
            display: 'flex',
            flexDirection: 'column',
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '16px 20px',
              borderBottom: '1px solid var(--line)',
            }}>
              <span style={{ fontSize: 18, fontWeight: 700 }}>执行日志</span>
              <button onClick={() => setDrawerOpen(false)} style={{
                background: 'none',
                border: 'none',
                fontSize: 20,
                cursor: 'pointer',
                color: 'var(--muted)',
              }}>✕</button>
            </div>
            <div style={{
              flex: 1,
              background: '#0b1522',
              padding: 16,
              fontFamily: 'monospace',
              fontSize: 13,
              color: '#d0d5dd',
              overflowY: 'auto',
            }}>
              {logs.length === 0 ? (
                <div style={{ color: '#667085' }}>等待实验启动...</div>
              ) : (
                logs.map((l, i) => (
                  <div key={i} style={{ marginBottom: 6 }}>
                    <span style={{ color: '#98a2b3' }}>[{l.time}]</span>{' '}
                    <span style={{
                      color: l.type === 'success' ? '#12b76a' : l.type === 'error' ? '#f04438' : '#d0d5dd'
                    }}>
                      {l.message}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
