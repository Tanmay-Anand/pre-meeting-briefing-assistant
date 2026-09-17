import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Panel } from './Panel'

const container = document.getElementById('root')
if (!container) {
  throw new Error('LeadLens panel: #root missing from index.html')
}

createRoot(container).render(
  <StrictMode>
    <Panel />
  </StrictMode>,
)
