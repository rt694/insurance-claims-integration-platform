import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import './index.css'
import { AuthenticatedPortal } from './auth/AuthenticatedPortal.tsx'
import { oidcConfiguration } from './auth/configuration.ts'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...oidcConfiguration}>
      <AuthenticatedPortal />
    </AuthProvider>
  </StrictMode>,
)
