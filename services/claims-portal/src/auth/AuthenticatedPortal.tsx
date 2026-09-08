import { useAuth } from 'react-oidc-context'
import App from '../App'
import { claimsRoles } from './roles'

export function AuthenticatedPortal() {
  const auth = useAuth()

  if (auth.activeNavigator === 'signinRedirect') {
    return <AuthenticationState message="Taking you to secure sign in…" />
  }
  if (auth.activeNavigator === 'signinSilent') {
    return <AuthenticationState message="Refreshing your secure session…" />
  }
  if (auth.activeNavigator === 'signoutRedirect') {
    return <AuthenticationState message="Signing you out…" />
  }
  if (auth.isLoading) {
    return <AuthenticationState message="Checking your secure session…" />
  }
  if (auth.error) {
    return (
      <AuthenticationCard
        eyebrow="Authentication problem"
        title="Sign-in could not be completed."
        detail="Return to the identity provider and try again. No claim data has been loaded."
        action="Try sign in again"
        onAction={() => void auth.signinRedirect()}
      />
    )
  }
  if (!auth.isAuthenticated || !auth.user) {
    return (
      <AuthenticationCard
        eyebrow="Claims operations"
        title="Sign in to the review workspace."
        detail="Use your assigned synthetic agent, reviewer, or administrator account."
        action="Sign in"
        onAction={() => void auth.signinRedirect()}
      />
    )
  }

  const roles = claimsRoles(auth.user.profile.roles)
  if (roles.length === 0) {
    return (
      <AuthenticationCard
        eyebrow="Access not assigned"
        title="Your account has no claims role."
        detail="Ask an administrator to assign AGENT, REVIEWER, or ADMIN before continuing."
        action="Sign out"
        onAction={() => void auth.signoutRedirect()}
      />
    )
  }

  const currentUser =
    stringClaim(auth.user.profile.preferred_username) ??
    stringClaim(auth.user.profile.name) ??
    auth.user.profile.sub

  return (
    <App
      accessToken={auth.user.access_token}
      currentUser={currentUser}
      roles={roles}
      onSignOut={() => void auth.signoutRedirect()}
    />
  )
}

function stringClaim(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function AuthenticationState({ message }: { message: string }) {
  return (
    <main className="authentication-shell" aria-live="polite">
      <div className="authentication-card authentication-loading">
        <span className="authentication-spinner" aria-hidden="true" />
        <p>{message}</p>
      </div>
    </main>
  )
}

interface AuthenticationCardProps {
  eyebrow: string
  title: string
  detail: string
  action: string
  onAction: () => void
}

function AuthenticationCard({
  eyebrow,
  title,
  detail,
  action,
  onAction,
}: AuthenticationCardProps) {
  return (
    <main className="authentication-shell">
      <section className="authentication-card" aria-labelledby="authentication-title">
        <span className="brand-mark" aria-hidden="true">CR</span>
        <p className="eyebrow">{eyebrow}</p>
        <h1 id="authentication-title">{title}</h1>
        <p>{detail}</p>
        <button className="primary-button" type="button" onClick={onAction}>
          {action}
        </button>
        <small>Synthetic insurance data only</small>
      </section>
    </main>
  )
}
