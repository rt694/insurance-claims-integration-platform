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

describe('Claims portal', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(claimPage)))
  })

  afterEach(cleanup)

  it('loads and displays claims from the service', async () => {
    render(<App />)

    expect(screen.getByText('Loading claims…')).toBeInTheDocument()
    expect(await screen.findByText('EXT-PORTAL-1001')).toBeInTheDocument()
    expect(screen.getByText('Synthetic Claimant')).toBeInTheDocument()
    expect(screen.getByText('1,250.00')).toBeInTheDocument()
    expect(screen.getByLabelText('Claim count')).toHaveTextContent('1')

    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/claims?page=0&size=10',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Correlation-ID': expect.any(String) }),
      }),
    )
  })

  it('sends selected filters and resets the page', async () => {
    const user = userEvent.setup()
    render(<App />)
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
    render(<App />)
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
    render(<App />)

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
    render(<App />)

    expect(await screen.findByText('No matching claims')).toBeInTheDocument()
    expect(screen.getByText('There is nothing in this view yet.')).toBeInTheDocument()
  })

  it('rejects a response that does not match the API contract', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ content: 'not-an-array' }))
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The claims service returned an unexpected response.',
    )
  })

  it('opens the submission form and prevents an invalid request', async () => {
    const user = userEvent.setup()
    render(<App />)
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
    render(<App />)
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
    render(<App />)
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
})
