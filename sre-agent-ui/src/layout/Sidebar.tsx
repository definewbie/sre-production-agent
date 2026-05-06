import { useState, useCallback, useEffect, useRef } from 'react'
import { Activity, Brain, FileSearch, FlaskConical, Monitor, Settings, ChevronLeft, ChevronRight } from 'lucide-react'
import { getTopBarEnvStatus, type TopBarEnvStatus } from '../api/client'

export interface PageInfo { id: string; label: string }

interface Props {
  pages: PageInfo[]
  currentPage: string
  onNavigate: (id: string) => void
}

const icons: Record<string, React.ReactNode> = {
  health: <Activity size={18} />,
  rca: <Brain size={18} />,
  evidence: <FileSearch size={18} />,
  chaos: <FlaskConical size={18} />,
  environment: <Monitor size={18} />,
  settings: <Settings size={18} />,
}

const MIN_WIDTH = 52
const MAX_WIDTH = 280
const DEFAULT_WIDTH = 160

export function Sidebar({ pages, currentPage, onNavigate }: Props) {
  const [collapsed, setCollapsed] = useState(false)
  const [width, setWidth] = useState(DEFAULT_WIDTH)
  const [envStatus, setEnvStatus] = useState<TopBarEnvStatus | null>(null)
  const dragging = useRef(false)
  const startX = useRef(0)
  const startWidth = useRef(0)

  // ── 环境摘要轮询 ──
  const fetchEnv = useCallback(async () => {
    const s = await getTopBarEnvStatus()
    setEnvStatus(s)
  }, [])

  useEffect(() => {
    fetchEnv()
    const id = setInterval(fetchEnv, 30000) // 30s 轮询
    return () => clearInterval(id)
  }, [fetchEnv])

  // ── 拖拽逻辑 ──
  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    dragging.current = true
    startX.current = e.clientX
    startWidth.current = width
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }, [width])

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!dragging.current) return
      const delta = e.clientX - startX.current
      const newWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, startWidth.current + delta))
      setWidth(newWidth)
      if (newWidth <= MIN_WIDTH + 10) {
        setCollapsed(true)
      } else {
        setCollapsed(false)
      }
    }
    const onMouseUp = () => {
      if (!dragging.current) return
      dragging.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
    return () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
  }, [])

  const toggleCollapse = () => {
    if (collapsed) {
      setCollapsed(false)
      setWidth(DEFAULT_WIDTH)
    } else {
      setCollapsed(true)
      setWidth(MIN_WIDTH)
    }
  }

  const effectiveWidth = collapsed ? MIN_WIDTH : width

  // ── 环境摘要渲染 ──
  const envOk = envStatus?.allOk
  const envErr = envStatus?.error

  return (
    <>
      <div
        className="sidebar"
        style={{ width: effectiveWidth }}
      >
        {/* Brand */}
        <div className="sidebar-brand" style={{ padding: collapsed ? 16 : '20px 24px', textAlign: 'center' }}>
          {collapsed ? 'S' : 'SRE Agent'}
        </div>

        {/* Nav */}
        <nav className="sidebar-nav">
          {pages.map(p => (
            <div
              key={p.id}
              className={'nav-item' + (currentPage === p.id ? ' active' : '')}
              style={{ justifyContent: collapsed ? 'center' : 'flex-start', padding: collapsed ? '10px 0' : '10px 20px' }}
              onClick={() => onNavigate(p.id)}
              title={collapsed ? p.label : undefined}
            >
              {icons[p.id]}
              {!collapsed && <span style={{ whiteSpace: 'nowrap' }}>{p.label}</span>}
            </div>
          ))}
        </nav>

        {/* Collapse toggle */}
        <div
          onClick={toggleCollapse}
          className="sidebar-collapse-btn"
          title={collapsed ? '展开导航' : '收起导航'}
        >
          {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
        </div>

        {/* Footer — 环境摘要 */}
        {!collapsed && (
          <div className="sidebar-footer">
            <div className="label">环境</div>
            <div className="env-name">
              local-kind-demo
              <span className={'status-dot ' + (envOk ? 'green' : (envErr ? 'red' : 'orange'))} />
            </div>
            {envStatus && !envErr ? (
              <div style={{ marginTop: 6, fontSize: 11, color: 'var(--sidebar-muted)' }}>
                <div>Prometheus: {envStatus.prometheus ? 'OK' : 'Failed'}</div>
                <div>Loki: {envStatus.loki ? 'OK' : 'Failed'}</div>
                <div>Jaeger: {envStatus.jaeger ? 'OK' : 'Failed'}</div>
                <div>Demo: {envStatus.demoServices ? 'OK' : 'Failed'}</div>
                <div>API: {envStatus.api ? 'OK' : 'Failed'}</div>
              </div>
            ) : envErr ? (
              <div style={{ marginTop: 6, fontSize: 11, color: '#fda29b' }}>
                环境异常 — {envErr.slice(0, 40)}
              </div>
            ) : (
              <div style={{ marginTop: 6, fontSize: 11, color: 'var(--sidebar-muted)' }}>
                正在检测...
              </div>
            )}
            <div className="env-time" style={{ marginTop: 6 }}>
              {new Date().toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
            </div>
          </div>
        )}
        {/* collapsed 时只显示小圆点 */}
        {collapsed && envStatus && (
          <div style={{ textAlign: 'center', padding: '4px 0' }}>
            <span className={'status-dot ' + (envOk ? 'green' : 'red')} />
          </div>
        )}

        {/* Resize handle */}
        <div className="sidebar-resize-handle" onMouseDown={onMouseDown} />
      </div>

      {/* Pass width to main-content via CSS variable */}
      <style>{'.main-content { margin-left: ' + effectiveWidth + 'px !important; }'}</style>
    </>
  )
}
