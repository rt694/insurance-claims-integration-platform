export const CLAIM_TYPES = ['AUTO', 'PROPERTY', 'LIFE', 'DISABILITY'] as const
export type ClaimType = (typeof CLAIM_TYPES)[number]

export const CLAIM_STATUSES = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'APPROVED',
  'DENIED',
  'CANCELLED',
  'CLOSED',
] as const
export type ClaimStatus = (typeof CLAIM_STATUSES)[number]

export interface Claim {
  id: string
  externalReference: string
  policyNumber: string
  claimantName: string
  claimType: ClaimType
  incidentDate: string
  description: string
  estimatedLoss: number
  status: ClaimStatus
  createdAt: string
  updatedAt: string
}

export interface ClaimPage {
  content: Claim[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateClaimInput {
  externalReference: string
  policyNumber: string
  claimantName: string
  claimType: ClaimType
  incidentDate: string
  description: string
  estimatedLoss: number
}

export const HUMAN_REVIEW_QUEUES = [
  'STANDARD_REVIEW',
  'COMPLEX_REVIEW',
  'SPECIALIST_REVIEW',
] as const
export type HumanReviewQueue = (typeof HUMAN_REVIEW_QUEUES)[number]

export interface ClaimSummary {
  claimId: string
  summary: string
  missingInformation: string[]
  recommendedHumanReviewQueue: HumanReviewQueue
  safetyFlags: string[]
  generatedAt: string
}

export interface ClaimStatusChange {
  id: string
  claimId: string
  previousStatus?: ClaimStatus | null
  newStatus: ClaimStatus
  changedAt: string
}

interface ClaimFilters {
  page: number
  size: number
  status?: ClaimStatus
  claimType?: ClaimType
}

interface ProblemDetails {
  type?: string
  status?: number
  detail?: string
  correlationId?: string
  errors?: Record<string, string>
}

export class ApiProblem extends Error {
  readonly status?: number
  readonly type?: string
  readonly correlationId?: string
  readonly fieldErrors: Record<string, string>

  constructor(message: string, problem?: ProblemDetails) {
    super(message)
    this.name = 'ApiProblem'
    this.status = problem?.status
    this.type = problem?.type
    this.correlationId = problem?.correlationId
    this.fieldErrors = problem?.errors ?? {}
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isClaim(value: unknown): value is Claim {
  return (
    isObject(value) &&
    typeof value.id === 'string' &&
    typeof value.externalReference === 'string' &&
    typeof value.policyNumber === 'string' &&
    typeof value.claimantName === 'string' &&
    CLAIM_TYPES.includes(value.claimType as ClaimType) &&
    typeof value.incidentDate === 'string' &&
    typeof value.description === 'string' &&
    typeof value.estimatedLoss === 'number' &&
    CLAIM_STATUSES.includes(value.status as ClaimStatus) &&
    typeof value.createdAt === 'string' &&
    typeof value.updatedAt === 'string'
  )
}

function isClaimPage(value: unknown): value is ClaimPage {
  return (
    isObject(value) &&
    Array.isArray(value.content) &&
    value.content.every(isClaim) &&
    typeof value.page === 'number' &&
    typeof value.size === 'number' &&
    typeof value.totalElements === 'number' &&
    typeof value.totalPages === 'number'
  )
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

function isClaimSummary(value: unknown): value is ClaimSummary {
  return (
    isObject(value) &&
    typeof value.claimId === 'string' &&
    typeof value.summary === 'string' &&
    isStringArray(value.missingInformation) &&
    HUMAN_REVIEW_QUEUES.includes(value.recommendedHumanReviewQueue as HumanReviewQueue) &&
    isStringArray(value.safetyFlags) &&
    typeof value.generatedAt === 'string'
  )
}

function isClaimStatusChange(value: unknown): value is ClaimStatusChange {
  return (
    isObject(value) &&
    typeof value.id === 'string' &&
    typeof value.claimId === 'string' &&
    (value.previousStatus == null ||
      CLAIM_STATUSES.includes(value.previousStatus as ClaimStatus)) &&
    CLAIM_STATUSES.includes(value.newStatus as ClaimStatus) &&
    typeof value.changedAt === 'string'
  )
}

function stringProperty(value: Record<string, unknown>, key: string): string | undefined {
  return typeof value[key] === 'string' ? value[key] : undefined
}

function stringRecord(value: unknown): Record<string, string> | undefined {
  if (!isObject(value)) return undefined
  const strings: Record<string, string> = {}
  for (const [field, message] of Object.entries(value)) {
    if (typeof message !== 'string') return undefined
    strings[field] = message
  }
  return strings
}

function problemDetails(value: unknown): ProblemDetails | undefined {
  if (!isObject(value)) return undefined
  return {
    type: stringProperty(value, 'type'),
    status: typeof value.status === 'number' ? value.status : undefined,
    detail: stringProperty(value, 'detail'),
    correlationId: stringProperty(value, 'correlationId'),
    errors: stringRecord(value.errors),
  }
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    return null
  }
}

function correlationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `claims-portal-${Date.now()}`
}

export async function listClaims(filters: ClaimFilters, signal?: AbortSignal): Promise<ClaimPage> {
  const query = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.status) query.set('status', filters.status)
  if (filters.claimType) query.set('claimType', filters.claimType)

  const response = await fetch(`/api/v1/claims?${query}`, {
    headers: {
      Accept: 'application/json',
      'X-Correlation-ID': correlationId(),
    },
    signal,
  })
  const body = await readJson(response)

  if (!response.ok) {
    const problem = problemDetails(body)
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (!isClaimPage(body)) {
    throw new ApiProblem('The claims service returned an unexpected response.')
  }
  return body
}

export async function createClaim(input: CreateClaimInput): Promise<Claim> {
  const response = await fetch('/api/v1/claims', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Correlation-ID': correlationId(),
    },
    body: JSON.stringify(input),
  })
  const body = await readJson(response)

  if (!response.ok) {
    const problem = problemDetails(body)
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (!isClaim(body)) {
    throw new ApiProblem('The claims service returned an unexpected response.')
  }
  return body
}

export async function getClaim(id: string, signal?: AbortSignal): Promise<Claim> {
  const response = await fetch(`/api/v1/claims/${encodeURIComponent(id)}`, {
    headers: {
      Accept: 'application/json',
      'X-Correlation-ID': correlationId(),
    },
    signal,
  })
  const body = await readJson(response)

  if (!response.ok) {
    const problem = problemDetails(body)
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (!isClaim(body)) {
    throw new ApiProblem('The claims service returned an unexpected claim response.')
  }
  return body
}

export async function getClaimSummary(
  claimId: string,
  signal?: AbortSignal,
): Promise<ClaimSummary> {
  const response = await fetch(`/api/v1/claims/${encodeURIComponent(claimId)}/summary`, {
    headers: {
      Accept: 'application/json',
      'X-Correlation-ID': correlationId(),
    },
    signal,
  })
  const body = await readJson(response)

  if (!response.ok) {
    const problem = problemDetails(body)
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (!isClaimSummary(body) || body.claimId !== claimId) {
    throw new ApiProblem('The claims service returned an unexpected summary response.')
  }
  return body
}

export async function getClaimHistory(
  claimId: string,
  signal?: AbortSignal,
): Promise<ClaimStatusChange[]> {
  const response = await fetch(`/api/v1/claims/${encodeURIComponent(claimId)}/history`, {
    headers: {
      Accept: 'application/json',
      'X-Correlation-ID': correlationId(),
    },
    signal,
  })
  const body = await readJson(response)

  if (!response.ok) {
    const problem = problemDetails(body)
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (
    !Array.isArray(body) ||
    !body.every((change) => isClaimStatusChange(change) && change.claimId === claimId)
  ) {
    throw new ApiProblem('The claims service returned an unexpected history response.')
  }
  return body
}

export async function updateClaimStatus(id: string, status: ClaimStatus): Promise<Claim> {
  const response = await fetch(`/api/v1/claims/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Correlation-ID': correlationId(),
    },
    body: JSON.stringify({ status }),
  })
  const body = await readJson(response)

  if (!response.ok) {
    const problem = problemDetails(body)
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (!isClaim(body) || body.id !== id) {
    throw new ApiProblem('The claims service returned an unexpected status response.')
  }
  return body
}
