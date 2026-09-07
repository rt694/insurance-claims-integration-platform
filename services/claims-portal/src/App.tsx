import { useEffect, useState } from 'react'
import './App.css'
import {
  ApiProblem,
  type ClaimPage,
  type ClaimStatus,
  type ClaimType,
  listClaims,
} from './api/claims'

const CLAIM_TYPES: ClaimType[] = ['AUTO', 'PROPERTY', 'LIFE', 'DISABILITY']
const CLAIM_STATUSES: ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'APPROVED',
  'DENIED',
  'CANCELLED',
  'CLOSED',
]

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
})
const numberFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

function displayLabel(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function App() {
  const [claimPage, setClaimPage] = useState<ClaimPage | null>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<ClaimStatus | ''>('')
  const [claimType, setClaimType] = useState<ClaimType | ''>('')
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<ApiProblem | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  function startRequest(updateQuery: () => void) {
    setIsLoading(true)
    setError(null)
    updateQuery()
  }

  useEffect(() => {
    const controller = new AbortController()

    listClaims(
      {
        page,
        size: 10,
        status: status || undefined,
        claimType: claimType || undefined,
      },
      controller.signal,
    )
      .then(setClaimPage)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setError(
            reason instanceof ApiProblem
              ? reason
              : new ApiProblem('The claims service could not be reached.'),
          )
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })

    return () => controller.abort()
  }, [claimType, page, reloadKey, status])

  const hasFilters = Boolean(status || claimType)
  const claims = claimPage?.content ?? []

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#main-content" aria-label="Claims Review Workspace home">
          <span className="brand-mark" aria-hidden="true">CR</span>
          <span>
            <strong>Claims Review</strong>
            <small>Operations workspace</small>
          </span>
        </a>
        <div className="environment-badge">
          <span aria-hidden="true" /> Synthetic data
        </div>
      </header>

      <main id="main-content">
        <section className="page-heading" aria-labelledby="claims-title">
          <div>
            <p className="eyebrow">Claims operations</p>
            <h1 id="claims-title">Review every claim with context.</h1>
            <p className="heading-copy">
              Track submissions, current status, and estimated loss before opening a claim for
              detailed human review.
            </p>
          </div>
          <div className="summary-card" aria-label="Claim count">
            <span>Total matching claims</span>
            <strong>{claimPage?.totalElements ?? '—'}</strong>
            <small>{hasFilters ? 'Filtered view' : 'Across all claim types'}</small>
          </div>
        </section>

        <section className="workspace" aria-labelledby="workspace-title">
          <div className="workspace-header">
            <div>
              <p className="section-kicker">Claim inventory</p>
              <h2 id="workspace-title">Submitted claims</h2>
            </div>
            <button
              className="refresh-button"
              type="button"
              onClick={() => startRequest(() => setReloadKey((key) => key + 1))}
            >
              Refresh data
            </button>
          </div>

          <form className="filters" onSubmit={(event) => event.preventDefault()}>
            <label>
              Status
              <select
                value={status}
                onChange={(event) => {
                  const nextStatus = event.target.value as ClaimStatus | ''
                  startRequest(() => {
                    setStatus(nextStatus)
                    setPage(0)
                  })
                }}
              >
                <option value="">All statuses</option>
                {CLAIM_STATUSES.map((option) => (
                  <option key={option} value={option}>{displayLabel(option)}</option>
                ))}
              </select>
            </label>
            <label>
              Claim type
              <select
                value={claimType}
                onChange={(event) => {
                  const nextClaimType = event.target.value as ClaimType | ''
                  startRequest(() => {
                    setClaimType(nextClaimType)
                    setPage(0)
                  })
                }}
              >
                <option value="">All claim types</option>
                {CLAIM_TYPES.map((option) => (
                  <option key={option} value={option}>{displayLabel(option)}</option>
                ))}
              </select>
            </label>
            {hasFilters && (
              <button
                className="clear-button"
                type="button"
                onClick={() =>
                  startRequest(() => {
                    setStatus('')
                    setClaimType('')
                    setPage(0)
                  })
                }
              >
                Clear filters
              </button>
            )}
          </form>

          <div className="results-region" aria-live="polite" aria-busy={isLoading}>
            {isLoading && !claimPage ? (
              <div className="state-panel loading-state">
                <span className="loading-line" />
                <span className="loading-line short" />
                <p>Loading claims…</p>
              </div>
            ) : error ? (
              <div className="state-panel error-state" role="alert">
                <p className="state-label">Unable to load claims</p>
                <h3>{error.message}</h3>
                {error.correlationId && <p>Reference: {error.correlationId}</p>}
                <button
                  type="button"
                  onClick={() => startRequest(() => setReloadKey((key) => key + 1))}
                >
                  Try again
                </button>
              </div>
            ) : claims.length === 0 ? (
              <div className="state-panel empty-state">
                <p className="state-label">No matching claims</p>
                <h3>There is nothing in this view yet.</h3>
                <p>Adjust the filters or submit a synthetic claim through the claims API.</p>
              </div>
            ) : (
              <div className="table-scroll">
                <table>
                  <caption className="visually-hidden">Claims matching the selected filters</caption>
                  <thead>
                    <tr>
                      <th scope="col">Reference</th>
                      <th scope="col">Claimant</th>
                      <th scope="col">Type</th>
                      <th scope="col">Incident date</th>
                      <th scope="col">Estimated loss</th>
                      <th scope="col">Status</th>
                      <th scope="col">Last updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    {claims.map((claim) => (
                      <tr key={claim.id}>
                        <td><strong>{claim.externalReference}</strong><small>{claim.id.slice(0, 8)}</small></td>
                        <td>{claim.claimantName}</td>
                        <td>{displayLabel(claim.claimType)}</td>
                        <td>{dateFormatter.format(new Date(`${claim.incidentDate}T00:00:00`))}</td>
                        <td className="number-cell">{numberFormatter.format(claim.estimatedLoss)}</td>
                        <td><span className={`status status-${claim.status.toLowerCase()}`}>{displayLabel(claim.status)}</span></td>
                        <td>{dateFormatter.format(new Date(claim.updatedAt))}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {claimPage && claimPage.totalPages > 0 && !error && (
            <nav className="pagination" aria-label="Claims pagination">
              <p>Page <strong>{claimPage.page + 1}</strong> of <strong>{claimPage.totalPages}</strong></p>
              <div>
                <button
                  type="button"
                  disabled={claimPage.page === 0 || isLoading}
                  onClick={() => startRequest(() => setPage((value) => value - 1))}
                >
                  Previous
                </button>
                <button
                  type="button"
                  disabled={claimPage.page + 1 >= claimPage.totalPages || isLoading}
                  onClick={() => startRequest(() => setPage((value) => value + 1))}
                >
                  Next
                </button>
              </div>
            </nav>
          )}
        </section>
      </main>

      <footer>
        Reviewer assistance only. Automated summaries never approve, deny, price, or determine
        coverage for a claim.
      </footer>
    </div>
  )
}

export default App
