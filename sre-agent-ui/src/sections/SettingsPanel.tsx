import { useState } from 'react'
import { defaultSettings } from '../data/mockData'
import { Settings, Save } from 'lucide-react'

export default function SettingsPanel() {
  const [settings, setSettings] = useState(defaultSettings)

  const update = (key: string, value: string | number | boolean) => {
    setSettings(prev => ({ ...prev, [key]: value }))
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">设置</h1>
          <div className="page-subtitle">SRE Agent 配置参数</div>
        </div>
        <button className="btn btn-primary btn-sm">
          <Save size={14} />
          保存设置
        </button>
      </div>

      <div className="settings-grid">
        {/* Left Column: Analysis */}
        <div className="card">
          <div className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Settings size={16} />
            分析参数
          </div>

          <div className="form-group">
            <label className="form-label">等待时间（秒）</label>
            <input
              className="form-input"
              type="number"
              value={settings.waitSeconds}
              onChange={e => update('waitSeconds', Number(e.target.value))}
            />
            <div className="form-hint">异常注入后等待数据采集的时间</div>
          </div>

          <div className="form-group">
            <label className="form-label">回溯窗口（秒）</label>
            <input
              className="form-input"
              type="number"
              value={settings.lookbackSeconds}
              onChange={e => update('lookbackSeconds', Number(e.target.value))}
            />
            <div className="form-hint">RCA 分析的数据回溯范围</div>
          </div>

          <div className="form-group">
            <label className="form-label">采集步长（秒）</label>
            <input
              className="form-input"
              type="number"
              value={settings.stepSeconds}
              onChange={e => update('stepSeconds', Number(e.target.value))}
            />
          </div>

          <div className="form-group">
            <label className="form-label">错误率阈值（%）</label>
            <input
              className="form-input"
              type="number"
              step="0.1"
              value={settings.errorRateThreshold}
              onChange={e => update('errorRateThreshold', Number(e.target.value))}
            />
          </div>

          <div className="form-group">
            <label className="form-label">P95 延迟阈值（ms）</label>
            <input
              className="form-input"
              type="number"
              value={settings.p95Threshold}
              onChange={e => update('p95Threshold', Number(e.target.value))}
            />
          </div>

          <div className="form-group">
            <label className="form-label">RCA 得分差阈值</label>
            <input
              className="form-input"
              type="number"
              step="0.01"
              value={settings.rcaScoreDiff}
              onChange={e => update('rcaScoreDiff', Number(e.target.value))}
            />
            <div className="form-hint">候选根因得分差小于此值时判定为"竞争假设"</div>
          </div>
        </div>

        {/* Right Column: System */}
        <div>
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="card-title">系统配置</div>

            <div className="form-group">
              <label className="form-label">API 地址</label>
              <input
                className="form-input"
                type="text"
                value={settings.apiBaseUrl}
                onChange={e => update('apiBaseUrl', e.target.value)}
              />
            </div>

            <div className="form-group">
              <label className="form-label">环境名称</label>
              <input
                className="form-input"
                type="text"
                value={settings.environmentName}
                onChange={e => update('environmentName', e.target.value)}
              />
            </div>

            <div className="setting-row">
              <div>
                <div style={{ fontWeight: 600 }}>自动刷新</div>
                <div className="form-hint">每隔 {settings.refreshInterval}s 自动刷新数据</div>
              </div>
              <div
                className={'toggle' + (settings.autoRefresh ? ' active' : '')}
                onClick={() => update('autoRefresh', !settings.autoRefresh)}
              >
                <div className="toggle-knob" />
              </div>
            </div>

            <div className="setting-row">
              <div>
                <div style={{ fontWeight: 600 }}>显示原始证据</div>
                <div className="form-hint">在证据页面显示完整 JSON</div>
              </div>
              <div
                className={'toggle' + (settings.showRawEvidence ? ' active' : '')}
                onClick={() => update('showRawEvidence', !settings.showRawEvidence)}
              >
                <div className="toggle-knob" />
              </div>
            </div>

            <div className="setting-row">
              <div>
                <div style={{ fontWeight: 600, color: 'var(--orange)' }}>使用模拟数据</div>
                <div className="form-hint">当前为演示模式，API 接入后关闭</div>
              </div>
              <div
                className={'toggle' + (settings.enableMockData ? ' active' : '')}
                onClick={() => update('enableMockData', !settings.enableMockData)}
              >
                <div className="toggle-knob" />
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-title">关于</div>
            <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 2 }}>
              <div><strong>SRE Production Agent</strong> v0.4.0</div>
              <div>确定性 RCA 决策代理 + 交互式工作台</div>
              <div>构建时间：2025-05-20</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
