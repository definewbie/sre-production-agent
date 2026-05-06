import { useState } from 'react'

interface Settings {
  waitSeconds: string
  lookbackSeconds: string
  stepSeconds: string
  apiBaseUrl: string
  environmentName: string
  refreshInterval: string
  autoRefresh: boolean
  showRawEvidence: boolean
  enableMockData: boolean
  errorRateThreshold: string
  p95Threshold: string
  rcaScoreDiff: string
}

function SettingInput({ label, value, onChange, unit, hint }: {
  label: string
  value: string
  onChange: (v: string) => void
  unit?: string
  hint?: string
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
      <label style={{ width: 150, fontSize: 14, color: 'var(--text)', flexShrink: 0 }}>{label}</label>
      <div style={{ display: 'flex', alignItems: 'center' }}>
        <input
          type="text"
          value={value}
          onChange={e => onChange(e.target.value)}
          style={{
            height: 34,
            border: '1px solid #c8d3e1',
            borderRadius: 6,
            padding: '0 12px',
            fontSize: 14,
            color: 'var(--text)',
            width: unit ? 120 : 260,
            outline: 'none',
          }}
        />
        {unit && <span style={{ marginLeft: 8, fontSize: 13, color: 'var(--muted)' }}>{unit}</span>}
      </div>
    </div>
  )
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: () => void }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
      <span style={{ fontSize: 14, color: 'var(--text)' }}>{label}</span>
      <div
        onClick={onChange}
        style={{
          width: 44,
          height: 24,
          borderRadius: 12,
          background: checked ? 'var(--blue)' : '#d0d5dd',
          position: 'relative',
          cursor: 'pointer',
          transition: 'background 0.2s',
          flexShrink: 0,
        }}
      >
        <div style={{
          width: 18,
          height: 18,
          borderRadius: '50%',
          background: '#fff',
          position: 'absolute',
          top: 3,
          left: checked ? 23 : 3,
          transition: 'left 0.2s',
        }} />
      </div>
    </div>
  )
}

export default function SettingsPanel() {
  const [s, setS] = useState<Settings>({
    waitSeconds: '30',
    lookbackSeconds: '300',
    stepSeconds: '15',
    apiBaseUrl: 'http://localhost:8080',
    environmentName: 'local-kind-demo',
    refreshInterval: '30',
    autoRefresh: true,
    showRawEvidence: false,
    enableMockData: true,
    errorRateThreshold: '1.0',
    p95Threshold: '1000',
    rcaScoreDiff: '0.10',
  })

  const upd = (key: keyof Settings, value: string | boolean) => {
    setS(prev => ({ ...prev, [key]: value }))
  }

  return (
    <div>
      {/* Breadcrumb */}
      <div className="breadcrumb" style={{ marginBottom: 4 }}>
        设置 ＞ 时间窗口 / API / 显示偏好
      </div>

      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <h1 className="page-title">7 设置</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-ghost btn-sm">重置</button>
          <button className="btn btn-primary btn-sm">保存</button>
        </div>
      </div>

      {/* 2x2 Grid */}
      <div style={{ display: 'flex', gap: 20, marginBottom: 20 }}>
        {/* Top Left: Time Window */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title" style={{ marginBottom: 16 }}>时间窗口默认值</div>
          <SettingInput label="故障注入后等待" value={s.waitSeconds} onChange={v => upd('waitSeconds', v)} unit="秒 waitSeconds" />
          <SettingInput label="证据查询窗口" value={s.lookbackSeconds} onChange={v => upd('lookbackSeconds', v)} unit="秒 lookbackSeconds" />
          <SettingInput label="查询粒度" value={s.stepSeconds} onChange={v => upd('stepSeconds', v)} unit="秒 stepSeconds" />
          <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4, lineHeight: 1.6 }}>
            说明：真实 RCA 需要覆盖 scrape / ingestion / trace 写入延迟，不建议使用过短窗口。
          </div>
        </div>

        {/* Top Right: API & Environment */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title" style={{ marginBottom: 16 }}>API 与环境</div>
          <SettingInput label="API Base URL" value={s.apiBaseUrl} onChange={v => upd('apiBaseUrl', v)} />
          <SettingInput label="环境名称" value={s.environmentName} onChange={v => upd('environmentName', v)} />
          <SettingInput label="刷新间隔" value={s.refreshInterval} onChange={v => upd('refreshInterval', v)} unit="秒" />
          <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4, lineHeight: 1.6 }}>
            说明：第一版可使用 mock data，后续逐区接入真实 API。
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 20 }}>
        {/* Bottom Left: Display Preferences */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title" style={{ marginBottom: 16 }}>显示偏好</div>
          <Toggle label="自动刷新" checked={s.autoRefresh} onChange={() => upd('autoRefresh', !s.autoRefresh)} />
          <Toggle label="显示 Raw Evidence" checked={s.showRawEvidence} onChange={() => upd('showRawEvidence', !s.showRawEvidence)} />
          <Toggle label="启用 Mock Data" checked={s.enableMockData} onChange={() => upd('enableMockData', !s.enableMockData)} />
          <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4, lineHeight: 1.6 }}>
            说明：mock data 必须明确标记，不能伪装成真实 API 数据。
          </div>
        </div>

        {/* Bottom Right: Alerts & Thresholds */}
        <div className="card" style={{ flex: 1, padding: 20 }}>
          <div className="card-title" style={{ marginBottom: 16 }}>告警与阈值</div>
          <SettingInput label="错误率异常阈值" value={s.errorRateThreshold} onChange={v => upd('errorRateThreshold', v)} unit="%" />
          <SettingInput label="P95 延迟异常阈值" value={s.p95Threshold} onChange={v => upd('p95Threshold', v)} unit="ms" />
          <SettingInput label="RCA 分数差阈值" value={s.rcaScoreDiff} onChange={v => upd('rcaScoreDiff', v)} />
          <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4, lineHeight: 1.6 }}>
            说明：阈值第一版仅用于前端展示，后续接入后端配置。
          </div>
        </div>
      </div>
    </div>
  )
}
