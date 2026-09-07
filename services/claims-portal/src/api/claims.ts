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
}

export class ApiProblem extends Error {
  readonly status?: number
  readonly type?: string
  readonly correlationId?: string

  constructor(message: string, problem?: ProblemDetails) {
    super(message)
    this.name = 'ApiProblem'
    this.status = problem?.status
    this.type = problem?.type
    this.correlationId = problem?.correlationId
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

function isProblemDetails(value: unknown): value is ProblemDetails {
  return isObject(value)
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
    const problem = isProblemDetails(body) ? body : undefined
    throw new ApiProblem(problem?.detail ?? `The claims service returned ${response.status}.`, problem)
  }
  if (!isClaimPage(body)) {
    throw new ApiProblem('The claims service returned an unexpected response.')
  }
  return body
}
