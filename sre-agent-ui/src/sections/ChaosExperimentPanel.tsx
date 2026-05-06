import { useState } from 'react'
import { FlaskConical, Play, RotateCcw, Clock, Zap } from 'lucide-react'

const CHAOS_TYPES = [
  { id: 'pod_kill', name: 'Pod Kill', desc: '随机终止目标服务的 Pod' },
  { id: 'network_delay', name: '网络延迟注入', desc: '在服务间注入网络延迟' },
  { id: 'cpu_stress', name: 'CPU 压力', desc: '对目标服务施加 CPU 负载' },
  { id: 'memory_leak', name: '内存泄漏模拟', desc: '模拟内存持续增长' },
]

const TARGET_SERVICES = ['order-service', 'payment-service', 'inventory-service']

interface ExperimentLog {
  time: string
  message: string
  type: 'info' | 'success' | 'error'
}

export default function ChaosExperimentPanel() {
  const [chaosType, setChaosType] = useState('pod_kill')
  const [target, setTarget] = useState('order-service')
  const [duration, setDuration] = useState('60')
  const [running, setRunning] = useState(false)
  const [logs, setLogs] = useState<ExperimentLog[]>([])

  const addLog = (message: string, type: ExperimentLog['type'] = 'info') => {
    const now = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    setLogs(prev => [...prev, { time: now, message, type }])
  }

  const handleRun = () => {
    setRunning(true)
    setLogs([])
    addLog('实验参数验证通过', 'info')
    setTimeout(() => addLog('连接 Kubernetes chaos namespace...', 'info'), 500)
    setTimeout(() => addLog('Chaos 资源已创建: ' + chaosType, 'success'), 1200)
    setTimeout(() => addLog('目标服务: ' + target + '，持续时间: ' + duration + 's', 'info'), 1800)
    setTimeout(() => {
      addLog('Chaos 实验已启动', 'success')
      setRunning(false)
    }, 2500)
  }

  const handleReset = () => {
    setLogs([])
    addLog('环境已重置', 'info')
  }

  const currentChaos = CHAOS_TYPES.find(c => c.id === chaosType)

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Chaos 实验</h1>
          <div className="page-subtitle">主动注入故障，验证系统韧性</div>
        </div>
      </div>

      <div className="grid-2">
        {/* Left: Config */}
        <div className="card">
          <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <FlaskConical size={16} />
            实验配置
          </div>

          <div className="form-group">
            <label className="form-label">故障类型</label>
            <select className="form-select" value={chaosType} onChange={e => setChaosType(e.target.value)}>
              {CHAOS_TYPES.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            <div className="form-hint">{currentChaos?.desc}</div>
          </div>

          <div className="form-group">
            <label className="form-label">目标服务</label>
            <select className="form-select" value={target} onChange={e => setTarget(e.target.value)}>
              {TARGET_SERVICES.map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">持续时间（秒）</label>
            <input className="form-input" type="number" value={duration} onChange={e => setDuration(e.target.value)} />
          </div>

          <div style={{ display: 'flex', gap: 12, marginTop: 20 }}>
            <button className="btn btn-primary" onClick={handleRun} disabled={running}>
              <Play size={16} />
              {running ? '启动中...' : '启动实验'}
            </button>
            <button className="btn btn-ghost" onClick={handleReset}>
              <RotateCcw size={16} />
              重置环境
            </button>
          </div>

          <div className="footer-note" style={{ marginTop: 12 }}>
            <Zap size={12} style={{ verticalAlign: 'middle' }} /> Chaos 实验仅在 demo 环境中可用
          </div>
        </div>

        {/* Right: Log */}
        <div className="card">
          <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Clock size={16} />
            执行日志
          </div>
          <div style={{
            background: '#0b1522',
            borderRadius: 8,
            padding: 16,
            fontFamily: 'monospace',
            fontSize: 13,
            color: '#d0d5dd',
            minHeight: 300,
            maxHeight: 400,
            overflowY: 'auto',
          }}>
            {logs.length === 0 ? (
              <div style={{ color: '#667085' }}>等待实验启动...</div>
            ) : (
              logs.map((l, i) => (
                <div key={i} style={{ marginBottom: 6 }}>
                  <span style={{ color: '#98a2b3' }}>[{l.time}]</span>{' '}
                  <span style={{
                    color: l.type === 'success' ? 'var(--green)' : l.type === 'error' ? 'var(--red)' : '#d0d5dd'
                  }}>
                    {l.message}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
