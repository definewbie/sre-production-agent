import { Activity, Brain, FileSearch, FlaskConical, Monitor, Settings } from 'lucide-react'

export interface PageInfo { id: string; label: string }

interface Props {
  pages: PageInfo[]
  currentPage: string
  onNavigate: (id: string) => void
}

const icons: Record<string, React.ReactNode> = {
  health: <Activity size={16} />,
  rca: <Brain size={16} />,
  evidence: <FileSearch size={16} />,
  chaos: <FlaskConical size={16} />,
  environment: <Monitor size={16} />,
  settings: <Settings size={16} />,
}

export function Sidebar({ pages, currentPage, onNavigate }: Props) {
  return (
    <div className="sidebar">
      <div className="sidebar-brand">SRE Agent</div>
      <nav className="sidebar-nav">
        {pages.map(p => (
          <div
            key={p.id}
            className={'nav-item' + (currentPage === p.id ? ' active' : '')}
            onClick={() => onNavigate(p.id)}
          >
            {icons[p.id]}
            {p.label}
          </div>
        ))}
      </nav>
      <div className="sidebar-footer">
        <div className="label">环境</div>
        <div className="env-name">
          local-kind-demo
          <span className="status-dot green" />
        </div>
        <div className="env-time">2025-05-20 14:30:25</div>
      </div>
    </div>
  )
}
