import { useEffect, useState } from 'react'
import {
  ApiProblem,
  getClaim,
  getClaimSummary,
  type Claim,
  type ClaimSummary,
} from './api/claims'

interface ClaimDetailPanelProps {
  claimId: string
  onClose: () => void
}

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  month: 'long',
  day: 'numeric',
  year: 'numeric',
})
const timestampFormatter = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
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

function apiProblem(reason: unknown, fallback: string) {
  return reason instanceof ApiProblem ? reason : new ApiProblem(fallback)
}

function isPendingSummary(problem: ApiProblem | null) {
  return problem?.status === 404 && problem.type === 'urn:problem:claim-summary-not-found'
}

export function ClaimDetailPanel({ claimId, onClose }: ClaimDetailPanelProps) {
  const [claim, setClaim] = useState<Claim | null>(null)
  const [claimProblem, setClaimProblem] = useState<ApiProblem | null>(null)
  const [isClaimLoading, setIsClaimLoading] = useState(true)
  const [claimReloadKey, setClaimReloadKey] = useState(0)
  const [summary, setSummary] = useState<ClaimSummary | null>(null)
  const [summaryProblem, setSummaryProblem] = useState<ApiProblem | null>(null)
  const [isSummaryLoading, setIsSummaryLoading] = useState(true)
  const [summaryReloadKey, setSummaryReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    getClaim(claimId, controller.signal)
      .then(setClaim)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setClaimProblem(apiProblem(reason, 'The claim details could not be loaded.'))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsClaimLoading(false)
      })

    return () => controller.abort()
  }, [claimId, claimReloadKey])

  useEffect(() => {
    const controller = new AbortController()

    getClaimSummary(claimId, controller.signal)
      .then(setSummary)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setSummaryProblem(apiProblem(reason, 'The claim summary could not be loaded.'))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsSummaryLoading(false)
      })

    return () => controller.abort()
  }, [claimId, summaryReloadKey])

  function reloadClaim() {
    setClaim(null)
    setClaimProblem(null)
    setIsClaimLoading(true)
    setClaimReloadKey((key) => key + 1)
  }

  function reloadSummary() {
    setSummary(null)
    setSummaryProblem(null)
    setIsSummaryLoading(true)
    setSummaryReloadKey((key) => key + 1)
  }

  return (
    <section className="claim-detail-panel" aria-labelledby="claim-detail-title">
      <div className="detail-heading">
        <div>
          <p className="section-kicker">Human review</p>
          <h2 id="claim-detail-title">Claim details</h2>
          <p>{claim?.externalReference ?? `Claim ${claimId.slice(0, 8)}`}</p>
        </div>
        <button type="button" onClick={onClose}>Close details</button>
      </div>

      <div className="detail-layout">
        <section className="claim-facts" aria-labelledby="claim-facts-title">
          <div className="detail-section-heading">
            <div>
              <p className="section-kicker">Source record</p>
              <h3 id="claim-facts-title">Submitted information</h3>
            </div>
            {claim && (
              <span className={`status status-${claim.status.toLowerCase()}`}>
                {displayLabel(claim.status)}
              </span>
            )}
          </div>

          {isClaimLoading ? (
            <DetailLoading label="Loading claim details…" />
          ) : claimProblem ? (
            <DetailError
              title="Unable to load claim"
              problem={claimProblem}
              onRetry={reloadClaim}
            />
          ) : claim ? (
            <>
              <dl className="claim-metadata">
                <Metadata label="Claimant" value={claim.claimantName} />
                <Metadata label="Policy number" value={claim.policyNumber} />
                <Metadata label="Claim type" value={displayLabel(claim.claimType)} />
                <Metadata
                  label="Incident date"
                  value={dateFormatter.format(new Date(`${claim.incidentDate}T00:00:00`))}
                />
                <Metadata
                  label="Estimated loss"
                  value={numberFormatter.format(claim.estimatedLoss)}
                />
                <Metadata label="Last updated" value={timestampFormatter.format(new Date(claim.updatedAt))} />
              </dl>
              <div className="claim-description">
                <h4>Incident description</h4>
                <p>{claim.description}</p>
              </div>
            </>
          ) : null}
        </section>

        <section className="reviewer-assistance" aria-labelledby="reviewer-assistance-title">
          <div className="detail-section-heading">
            <div>
              <p className="section-kicker">Automated summary</p>
              <h3 id="reviewer-assistance-title">Reviewer assistance</h3>
            </div>
            <span className="assistance-label">Advisory only</span>
          </div>
          <p className="assistance-disclaimer">
            This content organizes information for a human reviewer. It never approves, denies,
            prices, or determines coverage.
          </p>

          <div className="summary-region" aria-live="polite" aria-busy={isSummaryLoading}>
            {isSummaryLoading ? (
              <DetailLoading label="Loading reviewer assistance…" />
            ) : isPendingSummary(summaryProblem) ? (
              <div className="summary-pending" role="status">
                <strong>Summary processing</strong>
                <p>The claim is available now, but its generated summary is not ready yet.</p>
                <button type="button" onClick={reloadSummary}>
                  Refresh summary
                </button>
              </div>
            ) : summaryProblem ? (
              <DetailError
                title="Unable to load reviewer assistance"
                problem={summaryProblem}
                onRetry={reloadSummary}
              />
            ) : summary ? (
              <SummaryContent summary={summary} />
            ) : null}
          </div>
        </section>
      </div>
    </section>
  )
}

function Metadata({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function DetailLoading({ label }: { label: string }) {
  return (
    <div className="detail-loading">
      <span className="loading-line" />
      <span className="loading-line short" />
      <p>{label}</p>
    </div>
  )
}

interface DetailErrorProps {
  title: string
  problem: ApiProblem
  onRetry: () => void
}

function DetailError({ title, problem, onRetry }: DetailErrorProps) {
  return (
    <div className="detail-error" role="alert">
      <strong>{title}</strong>
      <p>{problem.message}</p>
      {problem.correlationId && <small>Reference: {problem.correlationId}</small>}
      <button type="button" onClick={onRetry}>Try again</button>
    </div>
  )
}

function SummaryContent({ summary }: { summary: ClaimSummary }) {
  return (
    <div className="summary-content">
      <p className="summary-copy">{summary.summary}</p>
      <div className="review-queue">
        <span>Recommended routing</span>
        <strong>{displayLabel(summary.recommendedHumanReviewQueue)}</strong>
      </div>
      <SummaryList
        title="Missing information"
        items={summary.missingInformation}
        emptyMessage="No missing information was identified."
      />
      <SummaryList
        title="Safety flags"
        items={summary.safetyFlags.map(displayLabel)}
        emptyMessage="No safety flags were returned."
      />
      <p className="generated-at">
        Generated {timestampFormatter.format(new Date(summary.generatedAt))}
      </p>
    </div>
  )
}

interface SummaryListProps {
  title: string
  items: string[]
  emptyMessage: string
}

function SummaryList({ title, items, emptyMessage }: SummaryListProps) {
  return (
    <div className="summary-list">
      <h4>{title}</h4>
      {items.length > 0 ? (
        <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
      ) : (
        <p>{emptyMessage}</p>
      )}
    </div>
  )
}
