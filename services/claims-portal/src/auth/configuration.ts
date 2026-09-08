import { WebStorageStateStore } from 'oidc-client-ts'

const portalOrigin = window.location.origin

export const oidcConfiguration = {
  authority:
    import.meta.env.VITE_OIDC_AUTHORITY ??
    'http://localhost:8090/realms/insurance-platform',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'claims-portal',
  redirect_uri: portalOrigin,
  post_logout_redirect_uri: portalOrigin,
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  onSigninCallback() {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
