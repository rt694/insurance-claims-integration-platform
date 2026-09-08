import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const claimPage = {
  content: [
    {
      id: '32962f8d-78bd-4fd9-889e-5e8753935f8e',
      externalReference: 'EXT-PORTAL-1001',
      policyNumber: 'POL-AUTO-1001',
      claimantName: 'Synthetic Claimant',
      claimType: 'AUTO',
      incidentDate: '2026-01-10',
      description: 'Synthetic vehicle damage',
      estimatedLoss: 1250,
      status: 'SUBMITTED',
      createdAt: '2026-01-11T12:00:00Z',
      updatedAt: '2026-01-11T12:00:00Z',
    },
  ],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
}

const claimSummary = {
  claimId: claimPage.content[0].id,
  summary: 'Vehicle damage requires human review.',
  missingInformation: ['Police report'],
  recommendedHumanReviewQueue: 'STANDARD_REVIEW',
  safetyFlags: ['DESCRIPTION_REQUIRES_REVIEW'],
  generatedAt: '2026-01-11T12:01:00Z',
}

const initialClaimHistory = [
  {
    id: '3edc75cf-5ce1-471a-937a-468b6711a597',
    claimId: claimPage.content[0].id,
    previousStatus: null,
    newStatus: 'SUBMITTED',
    changedAt: '2026-01-11T12:00:00Z',
  },
]

function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

async function fillValidClaim(user: ReturnType<typeof userEvent.setup>) {
  const form = within(screen.getByRole('form', { name: 'Submit synthetic claim' }))
  await user.type(form.getByLabelText('External reference'), 'EXT-CREATE-1002')
  await user.type(form.getByLabelText('Policy number'), 'POL-AUTO-1001')
  await user.type(form.getByLabelText('Claimant name'), 'Synthetic New Claimant')
  await user.selectOptions(form.getByLabelText('Claim type'), 'AUTO')
  await user.type(form.getByLabelText('Incident date'), '2020-01-10')
  await user.type(form.getByLabelText('Estimated loss'), '2500.50')
  await user.type(form.getByLabelText('Incident description'), 'Synthetic collision damage')
}

function renderApp(roles = ['ADMIN']) {
  return render(
    <App
      accessToken="synthetic-test-access-token"
      currentUser="synthetic-test-admin"
      roles={roles}
      onSignOut={vi.fn()}
    />,
  )
}

describe('Claims portal', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(claimPage)))
  })

  afterEach(cleanup)

  it('loads and displays claims from the service', async () => {
    renderApp()

    expect(screen.getByText('Loading claims…')).toBeInTheDocument()
    expect(await screen.findByText('EXT-PORTAL-1001')).toBeInTheDocument()
    expect(screen.getByText('Synthetic Claimant')).toBeInTheDocument()
    expect(screen.getByText('1,250.00')).toBeInTheDocument()
    expect(screen.getByLabelText('Claim count')).toHaveTextContent('1')

    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/claims?page=0&size=10',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer synthetic-test-access-token',
          'X-Correlation-ID': expect.any(String),
        }),
      }),
    )
  })

  it('shows the signed-in role and hides submission from reviewers', async () => {
    renderApp(['REVIEWER'])

    expect(await screen.findByText('EXT-PORTAL-1001')).toBeInTheDocument()
    expect(screen.getByText('synthetic-test-admin')).toBeInTheDocument()
    expect(screen.getByText('REVIEWER')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New claim' })).not.toBeInTheDocument()
  })

  it('sends selected filters and resets the page', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    await user.selectOptions(screen.getByLabelText('Status'), 'UNDER_REVIEW')
    await user.selectOptions(screen.getByLabelText('Claim type'), 'PROPERTY')

    await waitFor(() => {
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/claims?page=0&size=10&status=UNDER_REVIEW&claimType=PROPERTY',
        expect.any(Object),
      )
    })
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument()
  })

  it('requests the next server-side page', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ ...claimPage, totalElements: 11, totalPages: 2 }))
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    await user.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() => {
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/claims?page=1&size=10',
        expect.any(Object),
      )
    })
  })

  it('shows sanitized Problem Details and retries the request', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(
        {
          type: 'urn:problem:service-unavailable',
          status: 503,
          detail: 'Claims are temporarily unavailable.',
          correlationId: 'portal-test-503',
        },
        false,
        503,
      ),
    )
    renderApp()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Claims are temporarily unavailable.',
    )
    expect(screen.getByRole('alert')).toHaveTextContent('portal-test-503')

    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('EXT-PORTAL-1001')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('shows a clear empty state when no claims match', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse({ ...claimPage, content: [], totalElements: 0, totalPages: 0 }),
    )
    renderApp()

    expect(await screen.findByText('No matching claims')).toBeInTheDocument()
    expect(screen.getByText('There is nothing in this view yet.')).toBeInTheDocument()
  })

  it('rejects a response that does not match the API contract', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ content: 'not-an-array' }))
    renderApp()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The claims service returned an unexpected response.',
    )
  })

  it('opens the submission form and prevents an invalid request', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    await user.click(screen.getByRole('button', { name: 'New claim' }))
    expect(screen.getByRole('form', { name: 'Submit synthetic claim' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Submit claim' }))

    expect(screen.getByText('External reference is required.')).toBeInTheDocument()
    expect(screen.getByText('Incident date is required.')).toBeInTheDocument()
    expect(screen.getByLabelText('External reference')).toHaveAttribute('aria-invalid', 'true')
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('submits a valid claim and refreshes the inventory', async () => {
    const user = userEvent.setup()
    const createdClaim = {
      ...claimPage.content[0],
      id: '35f24679-162b-4f56-a1ef-d70d43328b0d',
      externalReference: 'EXT-CREATE-1002',
      claimantName: 'Synthetic New Claimant',
      description: 'Synthetic collision damage',
      estimatedLoss: 2500.5,
    }
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse(createdClaim, true, 201))
      .mockResolvedValueOnce(
        jsonResponse({ ...claimPage, content: [createdClaim, ...claimPage.content], totalElements: 2 }),
      )

    await user.click(screen.getByRole('button', { name: 'New claim' }))
    await fillValidClaim(user)
    await user.click(screen.getByRole('button', { name: 'Submit claim' }))

    expect(await screen.findByRole('status')).toHaveTextContent('EXT-CREATE-1002')
    expect(screen.queryByRole('form', { name: 'Submit synthetic claim' })).not.toBeInTheDocument()
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3))

    const postRequest = vi.mocked(fetch).mock.calls[1]
    expect(postRequest[0]).toBe('/api/v1/claims')
    expect(postRequest[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer synthetic-test-access-token',
          'Content-Type': 'application/json',
          'X-Correlation-ID': expect.any(String),
        }),
      }),
    )
    expect(JSON.parse(postRequest[1]?.body as string)).toEqual({
      externalReference: 'EXT-CREATE-1002',
      policyNumber: 'POL-AUTO-1001',
      claimantName: 'Synthetic New Claimant',
      claimType: 'AUTO',
      incidentDate: '2020-01-10',
      description: 'Synthetic collision damage',
      estimatedLoss: 2500.5,
    })
  })

  it('keeps the form open and maps backend field errors', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(
        {
          type: 'urn:problem:validation-error',
          status: 400,
          detail: 'The claim request is invalid.',
          correlationId: 'portal-create-400',
          errors: { externalReference: 'External reference is already in use.' },
        },
        false,
        400,
      ),
    )

    await user.click(screen.getByRole('button', { name: 'New claim' }))
    await fillValidClaim(user)
    await user.click(screen.getByRole('button', { name: 'Submit claim' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The claim request is invalid.')
    expect(screen.getByRole('alert')).toHaveTextContent('portal-create-400')
    expect(screen.getByText('External reference is already in use.')).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Submit synthetic claim' })).toBeInTheDocument()
  })

  it('loads authoritative claim details and reviewer assistance', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/summary')) return Promise.resolve(jsonResponse(claimSummary))
      if (url.endsWith('/history')) return Promise.resolve(jsonResponse(initialClaimHistory))
      if (url === `/api/v1/claims/${claimPage.content[0].id}`) {
        return Promise.resolve(jsonResponse(claimPage.content[0]))
      }
      return Promise.resolve(jsonResponse(claimPage))
    })
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    await user.click(screen.getByRole('button', { name: 'Review claim EXT-PORTAL-1001' }))

    expect(await screen.findByText('Vehicle damage requires human review.')).toBeInTheDocument()
    expect(screen.getByText('POL-AUTO-1001')).toBeInTheDocument()
    expect(screen.getByText('Synthetic vehicle damage')).toBeInTheDocument()
    expect(screen.getByText('Standard Review')).toBeInTheDocument()
    expect(screen.getByText('Police report')).toBeInTheDocument()
    expect(screen.getByText('Description Requires Review')).toBeInTheDocument()
    expect(screen.getByText(/never approves, denies, prices, or determines coverage/i)).toBeInTheDocument()

    expect(fetch).toHaveBeenCalledWith(
      `/api/v1/claims/${claimPage.content[0].id}/summary`,
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Correlation-ID': expect.any(String) }),
      }),
    )
  })

  it('gives agents a read-only claim status view', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/summary')) return Promise.resolve(jsonResponse(claimSummary))
      if (url.endsWith('/history')) return Promise.resolve(jsonResponse(initialClaimHistory))
      if (url === `/api/v1/claims/${claimPage.content[0].id}`) {
        return Promise.resolve(jsonResponse(claimPage.content[0]))
      }
      return Promise.resolve(jsonResponse(claimPage))
    })
    renderApp(['AGENT'])
    await screen.findByText('EXT-PORTAL-1001')

    await user.click(screen.getByRole('button', { name: 'Review claim EXT-PORTAL-1001' }))
    const detailPanel = await screen.findByRole('region', { name: 'Claim details' })

    expect(within(detailPanel).getByText('View-only status access')).toBeInTheDocument()
    expect(within(detailPanel).queryByLabelText('Next status')).not.toBeInTheDocument()
  })

  it('treats a missing summary as pending and refreshes it independently', async () => {
    const user = userEvent.setup()
    let summaryRequests = 0
    vi.mocked(fetch).mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/summary')) {
        summaryRequests += 1
        return Promise.resolve(
          summaryRequests === 1
            ? jsonResponse(
                {
                  type: 'urn:problem:claim-summary-not-found',
                  status: 404,
                  detail: `A summary is not yet available for claim ${claimPage.content[0].id}`,
                  correlationId: 'portal-summary-pending',
                },
                false,
                404,
              )
            : jsonResponse(claimSummary),
        )
      }
      if (url.endsWith('/history')) return Promise.resolve(jsonResponse(initialClaimHistory))
      if (url === `/api/v1/claims/${claimPage.content[0].id}`) {
        return Promise.resolve(jsonResponse(claimPage.content[0]))
      }
      return Promise.resolve(jsonResponse(claimPage))
    })
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    await user.click(screen.getByRole('button', { name: 'Review claim EXT-PORTAL-1001' }))

    expect(await screen.findByText('Summary processing')).toBeInTheDocument()
    expect(screen.getByText(/generated summary is not ready yet/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Refresh summary' }))

    expect(await screen.findByText('Vehicle damage requires human review.')).toBeInTheDocument()
    expect(summaryRequests).toBe(2)
  })

  it('allows a valid human status transition and refreshes claim history', async () => {
    const user = userEvent.setup()
    const updatedClaim = {
      ...claimPage.content[0],
      status: 'UNDER_REVIEW',
      updatedAt: '2026-01-11T13:00:00Z',
    }
    const updatedHistory = [
      ...initialClaimHistory,
      {
        id: 'b8f2c5b1-a22b-4b92-8221-2f8264147110',
        claimId: claimPage.content[0].id,
        previousStatus: 'SUBMITTED',
        newStatus: 'UNDER_REVIEW',
        changedAt: '2026-01-11T13:00:00Z',
      },
    ]
    let historyRequests = 0
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = String(input)
      if (url.endsWith('/summary')) return Promise.resolve(jsonResponse(claimSummary))
      if (url.endsWith('/history')) {
        historyRequests += 1
        return Promise.resolve(
          jsonResponse(historyRequests === 1 ? initialClaimHistory : updatedHistory),
        )
      }
      if (url.endsWith('/status') && init?.method === 'PATCH') {
        return Promise.resolve(jsonResponse(updatedClaim))
      }
      if (url === `/api/v1/claims/${claimPage.content[0].id}`) {
        return Promise.resolve(jsonResponse(claimPage.content[0]))
      }
      return Promise.resolve(jsonResponse(claimPage))
    })
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    const reviewButton = screen.getByRole('button', { name: 'Review claim EXT-PORTAL-1001' })
    await user.click(reviewButton)
    const detailPanel = await screen.findByRole('region', { name: 'Claim details' })
    expect(await within(detailPanel).findByText('Claim submitted')).toBeInTheDocument()
    expect(within(detailPanel).queryByRole('option', { name: 'Approved' })).not.toBeInTheDocument()

    await user.selectOptions(within(detailPanel).getByLabelText('Next status'), 'UNDER_REVIEW')
    await user.click(within(detailPanel).getByRole('button', { name: 'Update status' }))

    expect(await within(detailPanel).findByRole('status')).toHaveTextContent(
      'Status updated to Under Review.',
    )
    expect(await within(detailPanel).findByText('Submitted → Under Review')).toBeInTheDocument()
    expect(historyRequests).toBe(2)
    expect(within(reviewButton.closest('tr') as HTMLTableRowElement).getByText('Under Review'))
      .toBeInTheDocument()

    const patchRequest = vi.mocked(fetch).mock.calls.find(
      ([url, init]) => String(url).endsWith('/status') && init?.method === 'PATCH',
    )
    expect(patchRequest).toBeDefined()
    expect(JSON.parse(patchRequest?.[1]?.body as string)).toEqual({ status: 'UNDER_REVIEW' })
    expect(patchRequest?.[1]?.headers).toEqual(
      expect.objectContaining({
        Authorization: 'Bearer synthetic-test-access-token',
        'Content-Type': 'application/json',
        'X-Correlation-ID': expect.any(String),
      }),
    )
  })

  it('shows a rejected status transition without changing the claim', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = String(input)
      if (url.endsWith('/summary')) return Promise.resolve(jsonResponse(claimSummary))
      if (url.endsWith('/history')) return Promise.resolve(jsonResponse(initialClaimHistory))
      if (url.endsWith('/status') && init?.method === 'PATCH') {
        return Promise.resolve(
          jsonResponse(
            {
              type: 'urn:problem:invalid-claim-status-transition',
              status: 409,
              detail: 'The status changed before this update was submitted.',
              correlationId: 'portal-status-conflict',
            },
            false,
            409,
          ),
        )
      }
      if (url === `/api/v1/claims/${claimPage.content[0].id}`) {
        return Promise.resolve(jsonResponse(claimPage.content[0]))
      }
      return Promise.resolve(jsonResponse(claimPage))
    })
    renderApp()
    await screen.findByText('EXT-PORTAL-1001')

    await user.click(screen.getByRole('button', { name: 'Review claim EXT-PORTAL-1001' }))
    const detailPanel = await screen.findByRole('region', { name: 'Claim details' })
    await user.selectOptions(within(detailPanel).getByLabelText('Next status'), 'CANCELLED')
    await user.click(within(detailPanel).getByRole('button', { name: 'Update status' }))

    const alert = await within(detailPanel).findByRole('alert')
    expect(alert).toHaveTextContent('The status changed before this update was submitted.')
    expect(alert).toHaveTextContent('portal-status-conflict')
    expect(historyRequestsFor(fetch, '/history')).toBe(1)
  })
})

function historyRequestsFor(fetchMock: typeof fetch, pathEnding: string) {
  return vi.mocked(fetchMock).mock.calls.filter(([url]) => String(url).endsWith(pathEnding)).length
}
