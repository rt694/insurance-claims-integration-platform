import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useAuth } from 'react-oidc-context'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthenticatedPortal } from './AuthenticatedPortal'
import { claimsRoles } from './roles'

vi.mock('react-oidc-context', async () => {
  const actual = await vi.importActual<typeof import('react-oidc-context')>('react-oidc-context')
  return { ...actual, useAuth: vi.fn() }
})

function authenticationState(overrides: Record<string, unknown> = {}) {
  return {
    activeNavigator: undefined,
    isLoading: false,
    isAuthenticated: false,
    error: undefined,
    user: undefined,
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    signoutRedirect: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as ReturnType<typeof useAuth>
}

describe('Authenticated portal', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    vi.mocked(useAuth).mockReturnValue(authenticationState())
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('starts the authorization-code sign-in redirect', async () => {
    const user = userEvent.setup()
    const auth = authenticationState()
    vi.mocked(useAuth).mockReturnValue(auth)

    render(<AuthenticatedPortal />)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(auth.signinRedirect).toHaveBeenCalledOnce()
  })

  it('does not load claims for an authenticated account without an assigned role', () => {
    vi.mocked(useAuth).mockReturnValue(
      authenticationState({
        isAuthenticated: true,
        user: {
          access_token: 'token-without-role',
          profile: { sub: 'unassigned-user', roles: ['offline_access'] },
        },
      }),
    )

    render(<AuthenticatedPortal />)

    expect(screen.getByRole('heading', { name: 'Your account has no claims role.' }))
      .toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('passes the bearer token and reviewer role into the claims workspace', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue({
          content: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
        }),
      }),
    )
    vi.mocked(useAuth).mockReturnValue(
      authenticationState({
        isAuthenticated: true,
        user: {
          access_token: 'reviewer-access-token',
          profile: {
            sub: 'reviewer-subject',
            preferred_username: 'claims-reviewer',
            roles: ['REVIEWER'],
          },
        },
      }),
    )

    render(<AuthenticatedPortal />)

    expect(await screen.findByText('No matching claims')).toBeInTheDocument()
    expect(screen.getByText('claims-reviewer')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/claims?page=0&size=10',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer reviewer-access-token' }),
      }),
    )
  })
})

describe('claimsRoles', () => {
  it('keeps only recognized claims roles', () => {
    expect(claimsRoles(['offline_access', 'AGENT', 'REVIEWER', 42])).toEqual([
      'AGENT',
      'REVIEWER',
    ])
    expect(claimsRoles('ADMIN')).toEqual([])
  })
})
