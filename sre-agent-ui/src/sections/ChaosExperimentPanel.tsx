import { useState, useEffect, useCallback } from 'react'
import {
  type ChaosFaultType,
  type ChaosStatusResponse,
  type ChaosExperimentInfo,
  getChaosStatus,
  startChaosExperiment,
  stopChaosExperiment,
  resetChaosFaults,
} from '../api/client'

// --- Constants ---
const TARGET_SERVICES = ['order-service', 'payment-service', 'inventory-service']
const FAULT_TYPES: { value: ChaosFaultType; label: string }[] = [
  { value: 'latency', label: '延迟注入' },
  { value: 'error', label: '错误注入' },
  { value: 'timeout', label: '超时注入' },
  { value: 'resource_pressure', label: '资源压力' },
]

const FAULT_LABEL_MAP: Record<string, string> = {
  latency: '延迟注入',
  error: '错误注入',
  timeout: '超时注入',
  resource_pressure: '资源压力',
}

type ExperimentPhase = 'idle' | 'loading' | 'error'

// --- Sub-components ---
function SelectField({ label, value, options, onChange, disabled }: {
  label: string
  value: string
  options: string[]
  onChange: (v: string) => void
  disabled?: boolean
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
      <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0 }}>{label}</label>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        disabled={disabled}
        style={{
          flex: 1,
          height: 34,
          border: '1px solid #c8d3e1',
          borderRadius: 8,
          padding: '0 12px',
          fontSize: 14,
          color: 'var(--text)',
          background: disabled ? '#f2f4f7' : '#fff',
          outline: 'none',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
      >
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  )
}

function InputField({ label, value, onChange, unit, disabled }: {
  label: string
  value: string
  onChange: (v: string) => void
  unit?: string
  disabled?: boolean
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
      <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0 }}>{label}</label>
      <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
        <input
          type="number"
          value={value}
          onChange={e => onChange(e.target.value)}
          disabled={disabled}
          style={{
            height: 34,
            border: '1px solid #c8d3e1',
            borderRadius: 8,
            padding: '0 12px',
            fontSize: 14,
            color: 'var(--text)',
            width: 120,
            outline: 'none',
            background: disabled ? '#f2f4f7' : '#fff',
          }}
        />
        {unit && <span style={{ marginLeft: 8, fontSize: 13, color: 'var(--muted)' }}>{unit}</span>}
      </div>
    </div>
  )
}

function FaultTypeSelect({ label, value, onChange, disabled }: {
  label: string
  value: ChaosFaultType
  onChange: (v: ChaosFaultType) => void
  disabled?: boolean
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
      <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0 }}>{label}</label>
      <select
        value={value}
        onChange={e => onChange(e.target.value as ChaosFaultType)}
        disabled={disabled}
        style={{
          flex: 1,
          height: 34,
          border: '1px solid #c8d3e1',
          borderRadius: 8,
          padding: '0 12px',
          fontSize: 14,
          color: 'var(--text)',
          background: disabled ? '#f2f4f7' : '#fff',
          outline: 'none',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
      >
        {FAULT_TYPES.map(ft => (
          <option key={ft.value} value={ft.value}>{ft.label}</option>
        ))}
      </select>
    </div>
  )
}

// --- Status badge component ---
function StatusBadge({ status }: { status: string }) {
  const colorMap: Record<string, { bg: string; fg: string }> = {
    RUNNING: { bg: '#d1fadf', fg: '#065f46' },
    STOPPED: { bg: '#fef3c7', fg: '#92400e' },
    FAILED: { bg: '#fee2e2', fg: '#991b1b' },
    IDLE: { bg: '#f2f4f7', fg: '#475467' },
  }
  const c = colorMap[status] || colorMap.IDLE
  return (
    <span style={{
      display: 'inline-block',
      padding: '2px 10px',
      borderRadius: 12,
      fontSize: 12,
      fontWeight: 600,
      background: c.bg,
      color: c.fg,
    }}>
      {status === 'IDLE' ? '空闲' : status}
    </span>
  )
}

// --- Main Component ---
export default function ChaosExperimentPanel() {
  // Config state
  const [target, setTarget] = useState('payment-service')
  const [faultType, setFaultType] = useState<ChaosFaultType>('latency')
  const [latency, setLatency] = useState('1500')
  const [errorRate, setErrorRate] = useState('0.5')
  const [duration, setDuration] = useState('300')
  const [rps, setRps] = useState('2')
  const [expName, setExpName] = useState('')
  const [expDesc, setExpDesc] = useState('')

  // Experiment state
  const [phase, setPhase] = useState<ExperimentPhase>('idle')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [activeExp, setActiveExp] = useState<ChaosExperimentInfo | null>(null)
  const [lastResult, setLastResult] = useState<{ status: string; message?: string } | null>(null)

  // Auto-generate experiment name from config
  useEffect(() => {
    const label = FAULT_LABEL_MAP[faultType] || faultType
    setExpName(`${target}-${faultType}-${Date.now() % 100000}`)
    setExpDesc(`向 ${target} 注入 ${label} 故障，持续 ${duration} 秒，RPS=${rps}`)
  }, [target, faultType, duration, rps])

  // Poll chaos status on mount and after actions
  const refreshStatus = useCallback(async () => {
    const { data } = await getChaosStatus()
    if (data) {
      const running = data.activeExperiments.find(e => e.active)
      setActiveExp(running ?? null)
    }
  }, [])

  useEffect(() => {
    refreshStatus()
  }, [refreshStatus])

  // --- Action Handlers ---
  const handleStart = async () => {
    setPhase('loading')
    setErrorMsg(null)
    const latencyMs = parseInt(latency, 10) || 0
    const errorRateVal = parseFloat(errorRate) || 0
    const durationSec = parseInt(duration, 10) || 300

    const { data, error } = await startChaosExperiment({
      targetService: target,
      faultType,
      latencyMs: latencyMs > 0 ? latencyMs : undefined,
      errorRate: faultType === 'error' ? errorRateVal : undefined,
      durationSeconds: durationSec,
      rps: parseInt(rps, 10) || 2,
      experimentName: expName,
      description: expDesc,
    })

    if (error) {
      setPhase('error')
      setErrorMsg(error)
      return
    }
    if (!data) {
      setPhase('error')
      setErrorMsg('启动失败：无响应数据')
      return
    }
    if (data.error) {
      setPhase('error')
      setErrorMsg(data.error)
      return
    }

    setPhase('idle')
    setLastResult({ status: data.status, message: data.message })
    if (data.experiment) setActiveExp(data.experiment)
    await refreshStatus()
  }

  const handleStop = async () => {
    const svc = activeExp?.targetService || target
    setPhase('loading')
    setErrorMsg(null)

    const { data, error } = await stopChaosExperiment(svc)
    if (error) {
      setPhase('error')
      setErrorMsg(error)
      return
    }
    if (!data) {
      setPhase('error')
      setErrorMsg('停止失败：无响应数据')
      return
    }
    if (data.error) {
      setPhase('error')
      setErrorMsg(data.error)
      return
    }

    setPhase('idle')
    setLastResult({ status: data.status, message: data.message })
    setActiveExp(null)
    await refreshStatus()
  }

  const handleReset = async () => {
    setPhase('loading')
    setErrorMsg(null)

    const { data, error } = await resetChaosFaults()
    if (error) {
      setPhase('error')
      setErrorMsg(error)
      return
    }
    if (!data) {
      setPhase('error')
      setErrorMsg('恢复失败：无响应数据')
      return
    }
    if (data.error) {
      setPhase('error')
      setErrorMsg(data.error)
      return
    }

    setPhase('idle')
    setLastResult({ status: data.status, message: data.message })
    setActiveExp(null)
    await refreshStatus()
  }

  const handleSave = () => {
    setLastResult({ status: 'SAVED', message: '配置已保存（仅前端保存）' })
  }

  // --- Helpers ---
  const isRunning = activeExp?.active ?? false
  const isBusy = phase === 'loading'

  const faultFieldsDisabled = (faultType: ChaosFaultType): Record<string, boolean> => {
    return {
      latency: faultType !== 'latency',
      errorRate: faultType !== 'error',
    }
  }

  const ff = faultFieldsDisabled(faultType)

  return (
    <div style={{ position: 'relative' }}>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        Lab Demo ＞ Chaos 实验
        <span style={{
          marginLeft: 12,
          padding: '2px 8px',
          borderRadius: 4,
          background: '#ffab00',
          color: '#fff',
          fontSize: 11,
          fontWeight: 700,
        }}>
          LAB DEMO
        </span>
      </div>

      {/* Page Header */}
      <h1 className="page-title" style={{ marginBottom: 8 }}>Chaos 实验配置</h1>
      <p style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 20, marginTop: 0 }}>
        仅用于演示环境，请谨慎使用故障注入功能。生产 RCA 请通过 Alertmanager 告警入口触发。
      </p>

      {/* Error Banner */}
      {errorMsg && (
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fca5a5',
          borderRadius: 8,
          padding: '10px 16px',
          marginBottom: 16,
          color: '#991b1b',
          fontSize: 14,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <span>❌ {errorMsg}</span>
          <button
            onClick={() => setErrorMsg(null)}
            style={{ background: 'none', border: 'none', fontSize: 16, cursor: 'pointer', color: '#991b1b' }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Current Experiment Status */}
      {activeExp && (
        <div style={{
          background: isRunning ? '#ecfdf5' : '#fef3c7',
          border: `1px solid ${isRunning ? '#a7f3d0' : '#fde68a'}`,
          borderRadius: 8,
          padding: '12px 16px',
          marginBottom: 16,
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>
              当前实验：{activeExp.experimentName}
            </span>
            <StatusBadge status={isRunning ? 'RUNNING' : 'STOPPED'} />
          </div>
          <div style={{ fontSize: 13, color: 'var(--text)', display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <span>目标: {activeExp.targetService}</span>
            <span>故障: {FAULT_LABEL_MAP[activeExp.faultType] || activeExp.faultType}</span>
            <span>启动: {new Date(activeExp.startedAt).toLocaleTimeString('zh-CN', { hour12: false })}</span>
            {isRunning && <span>剩余: {activeExp.remainingSeconds}s</span>}
          </div>
          {isRunning && (
            <div style={{ marginTop: 10, fontSize: 13, color: '#065f46' }}>
              💡 实验已启动。请等待 30–60 秒，随后通过左侧菜单栏查看「服务健康」或「RCA 分析」
            </div>
          )}
        </div>
      )}

      {/* Last result */}
      {lastResult && !activeExp && (
        <div style={{
          background: '#f2f4f7',
          borderRadius: 8,
          padding: '8px 12px',
          marginBottom: 12,
          fontSize: 13,
          color: 'var(--text)',
        }}>
          ✓ {lastResult.message || lastResult.status}
        </div>
      )}

      {/* Main Layout */}
      <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
        {/* Left: Config Form */}
        <div className="card" style={{ flex: 2, padding: 24 }}>
          <div className="card-title" style={{ marginBottom: 20 }}>故障注入配置</div>

          <SelectField
            label="目标服务"
            value={target}
            options={TARGET_SERVICES}
            onChange={setTarget}
            disabled={isRunning}
          />
          <FaultTypeSelect
            label="故障类型"
            value={faultType}
            onChange={setFaultType}
            disabled={isRunning}
          />
          <InputField
            label="延迟强度"
            value={latency}
            onChange={setLatency}
            unit="ms"
            disabled={ff.latency}
          />
          <InputField
            label="错误率"
            value={errorRate}
            onChange={setErrorRate}
            unit="0.0–1.0"
            disabled={ff.errorRate}
          />
          <InputField
            label="持续时间"
            value={duration}
            onChange={setDuration}
            unit="秒"
            disabled={isRunning}
          />
          <InputField
            label="RPS"
            value={rps}
            onChange={setRps}
            disabled={isRunning}
          />

          {faultType === 'resource_pressure' && (
            <div style={{
              padding: '10px 12px',
              background: '#fff7ed',
              border: '1px solid #fed7aa',
              borderRadius: 8,
              marginBottom: 20,
              fontSize: 13,
              color: '#92400e',
            }}>
              ⚠️ 资源压力注入尚未实现（resource_pressure not yet implemented）
            </div>
          )}

          <div style={{ display: 'flex', alignItems: 'flex-start', marginBottom: 20 }}>
            <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0, paddingTop: 8 }}>
              实验名称
            </label>
            <input
              type="text"
              value={expName}
              onChange={e => setExpName(e.target.value)}
              disabled={isRunning}
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
            <label style={{ width: 100, fontSize: 14, color: 'var(--text)', flexShrink: 0, paddingTop: 8 }}>
              实验描述
            </label>
            <textarea
              value={expDesc}
              onChange={e => setExpDesc(e.target.value)}
              disabled={isRunning}
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
              { label: '故障类型', value: FAULT_LABEL_MAP[faultType] || faultType },
              { label: '延迟强度', value: faultType === 'latency' ? latency + ' ms' : '—' },
              { label: '错误率', value: faultType === 'error' ? errorRate : '—' },
              { label: '持续时间', value: duration + ' 秒' },
              { label: 'RPS', value: rps },
            ].map((row, i) => (
              <div
                key={i}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '8px 0',
                  borderTop: i > 0 ? '1px solid var(--line)' : 'none',
                }}
              >
                <span style={{ fontSize: 14, color: 'var(--text)' }}>{row.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--title)' }}>{row.value}</span>
              </div>
            ))}
            <div style={{
              marginTop: 12,
              padding: '8px 12px',
              background: '#f2f4f7',
              borderRadius: 6,
              fontSize: 13,
              color: 'var(--muted)',
            }}>
              <div>预计影响：</div>
              <div style={{ marginTop: 4 }}>
                将向 <strong>{target}</strong> 注入 {FAULT_LABEL_MAP[faultType]} 故障{faultType === 'latency' ? `（${latency}ms延迟）` : ''}，持续 {duration} 秒。
                建议随后在服务健康页观察 {target} 指标变化，并等待 Alertmanager 产生相应告警。
              </div>
            </div>
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
      <div style={{ display: 'flex', gap: 12, marginTop: 20, alignItems: 'center' }}>
        <button className="btn btn-ghost" onClick={handleSave} disabled={isBusy}>
          保存配置
        </button>
        <button
          className="btn btn-primary"
          onClick={handleStart}
          disabled={isBusy || isRunning}
        >
          {isBusy && phase === 'loading' ? '启动中...' : '启动实验'}
        </button>
        <button
          style={{
            background: '#fff',
            border: '1px solid #fda29b',
            borderRadius: 8,
            padding: '0 16px',
            height: 38,
            fontSize: 14,
            fontWeight: 600,
            color: '#b42318',
            cursor: isBusy ? 'not-allowed' : 'pointer',
            opacity: isBusy ? 0.6 : 1,
          }}
          onClick={handleStop}
          disabled={isBusy}
        >
          停止实验
        </button>
        <button className="btn btn-ghost" onClick={handleReset} disabled={isBusy}>
          恢复正常
        </button>
        <button
          className="btn btn-ghost"
          onClick={refreshStatus}
          disabled={isBusy}
          style={{ marginLeft: 'auto' }}
        >
          刷新状态
        </button>
      </div>
    </div>
  )
}
