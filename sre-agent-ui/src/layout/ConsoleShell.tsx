import { ReactNode } from 'react'
import { Sidebar, PageInfo } from './Sidebar'

interface Props {
  pages: PageInfo[]
  currentPage: string
  onNavigate: (id: string) => void
  children: ReactNode
}

export function ConsoleShell({ pages, currentPage, onNavigate, children }: Props) {
  return (
    <div className="console-layout">
      <Sidebar pages={pages} currentPage={currentPage} onNavigate={onNavigate} />
      <main className="main-content">
        {children}
      </main>
    </div>
  )
}
