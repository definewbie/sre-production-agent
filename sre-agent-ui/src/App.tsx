import { useState } from 'react'
import { ConsoleShell } from './layout/ConsoleShell'
import ServiceHealthOverview from './sections/ServiceHealthOverview'
import IncidentDetailPanel from './sections/IncidentDetailPanel'
import RcaAnalysisPanel from './sections/RcaAnalysisPanel'
import EvidenceDrilldownPanel from './sections/EvidenceDrilldownPanel'
import ChaosExperimentPanel from './sections/ChaosExperimentPanel'
import EnvironmentStatusPanel from './sections/EnvironmentStatusPanel'
import SettingsPanel from './sections/SettingsPanel'

export type PageId = 'health' | 'incident' | 'rca' | 'evidence' | 'chaos' | 'environment' | 'settings'

const pages: { id: PageId; label: string }[] = [
  { id: 'health', label: '服务健康' },
  { id: 'rca', label: 'RCA 分析' },
  { id: 'evidence', label: '证据明细' },
  { id: 'chaos', label: 'Chaos 实验' },
  { id: 'environment', label: '环境状态' },
  { id: 'settings', label: '设置' },
]

export default function App() {
  const [currentPage, setCurrentPage] = useState<PageId>('health')
  const [incidentService, setIncidentService] = useState<string | null>(null)
  const [alertIncidentId, setAlertIncidentId] = useState<string | null>(null)

  const navigateToIncident = (serviceName: string) => {
    setIncidentService(serviceName)
    setCurrentPage('incident')
  }

  const handleRcaTriggered = (incidentId: string) => {
    setAlertIncidentId(incidentId)
    setCurrentPage('rca')
  }

  const renderPage = () => {
    switch (currentPage) {
      case 'health':
        return <ServiceHealthOverview onServiceClick={navigateToIncident} onRcaTriggered={handleRcaTriggered} />
      case 'incident':
        return <IncidentDetailPanel serviceName={incidentService} onBack={() => setCurrentPage('health')} onRca={() => setCurrentPage('rca')} />
      case 'rca':
        return <RcaAnalysisPanel alertIncidentId={alertIncidentId} />
      case 'evidence':
        return <EvidenceDrilldownPanel />
      case 'chaos':
        return <ChaosExperimentPanel />
      case 'environment':
        return <EnvironmentStatusPanel />
      case 'settings':
        return <SettingsPanel />
      default:
        return <ServiceHealthOverview onServiceClick={navigateToIncident} onRcaTriggered={handleRcaTriggered} />
    }
  }

  return (
    <ConsoleShell
      pages={pages}
      currentPage={currentPage}
      onNavigate={(id) => setCurrentPage(id as PageId)}
    >
      {renderPage()}
    </ConsoleShell>
  )
}
