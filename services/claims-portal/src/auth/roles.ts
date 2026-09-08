const CLAIMS_ROLES = ['AGENT', 'REVIEWER', 'ADMIN'] as const

export function claimsRoles(claim: unknown): string[] {
  if (!Array.isArray(claim)) return []
  return claim.filter(
    (role): role is string =>
      typeof role === 'string' && CLAIMS_ROLES.includes(role as (typeof CLAIMS_ROLES)[number]),
  )
}
