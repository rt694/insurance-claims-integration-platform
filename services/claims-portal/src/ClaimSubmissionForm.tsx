import { useState, type FormEvent, type ReactNode } from 'react'
import {
  ApiProblem,
  CLAIM_TYPES,
  createClaim,
  type Claim,
  type ClaimType,
  type CreateClaimInput,
} from './api/claims'

interface ClaimSubmissionFormProps {
  onCancel: () => void
  onCreated: (claim: Claim) => void
}

interface FormValues {
  externalReference: string
  policyNumber: string
  claimantName: string
  claimType: ClaimType | ''
  incidentDate: string
  description: string
  estimatedLoss: string
}

type FormField = keyof FormValues
type FormErrors = Partial<Record<FormField, string>>

const formFields: FormField[] = [
  'externalReference',
  'policyNumber',
  'claimantName',
  'claimType',
  'incidentDate',
  'description',
  'estimatedLoss',
]

const initialValues: FormValues = {
  externalReference: '',
  policyNumber: '',
  claimantName: '',
  claimType: '',
  incidentDate: '',
  description: '',
  estimatedLoss: '',
}
const identifierPattern = /^[A-Za-z0-9][A-Za-z0-9._-]*$/
const lossPattern = /^\d{1,13}(\.\d{1,2})?$/

function today(): string {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function validate(values: FormValues): FormErrors {
  const errors: FormErrors = {}
  const identifiers: Array<['externalReference' | 'policyNumber', string]> = [
    ['externalReference', 'External reference'],
    ['policyNumber', 'Policy number'],
  ]

  for (const [field, label] of identifiers) {
    const value = values[field].trim()
    if (!value) errors[field] = `${label} is required.`
    else if (value.length > 64) errors[field] = `${label} must be 64 characters or fewer.`
    else if (!identifierPattern.test(value)) {
      errors[field] = `${label} may contain letters, numbers, periods, underscores, and hyphens.`
    }
  }

  const claimantName = values.claimantName.trim()
  if (!claimantName) errors.claimantName = 'Claimant name is required.'
  else if (claimantName.length > 200) {
    errors.claimantName = 'Claimant name must be 200 characters or fewer.'
  }

  if (!values.claimType) errors.claimType = 'Claim type is required.'
  if (!values.incidentDate) errors.incidentDate = 'Incident date is required.'
  else if (values.incidentDate > today()) {
    errors.incidentDate = 'Incident date cannot be in the future.'
  }

  const description = values.description.trim()
  if (!description) errors.description = 'Description is required.'
  else if (description.length > 4_000) {
    errors.description = 'Description must be 4,000 characters or fewer.'
  }

  if (!values.estimatedLoss) errors.estimatedLoss = 'Estimated loss is required.'
  else if (!lossPattern.test(values.estimatedLoss) || Number(values.estimatedLoss) < 0) {
    errors.estimatedLoss = 'Enter a non-negative amount with no more than two decimal places.'
  }
  return errors
}

function inputFrom(values: FormValues): CreateClaimInput {
  return {
    externalReference: values.externalReference,
    policyNumber: values.policyNumber,
    claimantName: values.claimantName,
    claimType: values.claimType as ClaimType,
    incidentDate: values.incidentDate,
    description: values.description,
    estimatedLoss: Number(values.estimatedLoss),
  }
}

function formErrorsFrom(fieldErrors: Record<string, string>): FormErrors {
  return Object.fromEntries(
    formFields.flatMap((field) => fieldErrors[field] ? [[field, fieldErrors[field]]] : []),
  )
}

export function ClaimSubmissionForm({ onCancel, onCreated }: ClaimSubmissionFormProps) {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState<FormErrors>({})
  const [problem, setProblem] = useState<ApiProblem | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  function update(field: FormField, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validationErrors = validate(values)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      setProblem(null)
      return
    }

    setIsSubmitting(true)
    setProblem(null)
    try {
      const claim = await createClaim(inputFrom(values))
      onCreated(claim)
    } catch (reason) {
      const apiProblem =
        reason instanceof ApiProblem
          ? reason
          : new ApiProblem('The claim could not be submitted at this time.')
      setProblem(apiProblem)
      setErrors(formErrorsFrom(apiProblem.fieldErrors))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="submission-panel" aria-labelledby="submission-title">
      <div className="submission-heading">
        <div>
          <p className="section-kicker">New submission</p>
          <h2 id="submission-title">Create a synthetic claim</h2>
          <p>Policy coverage is checked before the claim is stored.</p>
        </div>
        <button type="button" onClick={onCancel} disabled={isSubmitting}>Close</button>
      </div>

      {problem && (
        <div className="form-problem" role="alert">
          <strong>Claim not submitted</strong>
          <p>{problem.message}</p>
          {problem.correlationId && <small>Reference: {problem.correlationId}</small>}
        </div>
      )}

      <form className="claim-form" aria-label="Submit synthetic claim" noValidate onSubmit={submit}>
        <div className="form-grid">
          <Field name="externalReference" label="External reference" error={errors.externalReference}>
            <input
              id="externalReference"
              name="externalReference"
              value={values.externalReference}
              onChange={(event) => update('externalReference', event.target.value)}
              maxLength={64}
              aria-invalid={Boolean(errors.externalReference)}
              aria-describedby={errors.externalReference ? 'externalReference-error' : undefined}
              autoComplete="off"
            />
          </Field>
          <Field name="policyNumber" label="Policy number" error={errors.policyNumber}>
            <input
              id="policyNumber"
              name="policyNumber"
              value={values.policyNumber}
              onChange={(event) => update('policyNumber', event.target.value)}
              maxLength={64}
              aria-invalid={Boolean(errors.policyNumber)}
              aria-describedby={errors.policyNumber ? 'policyNumber-error' : undefined}
              autoComplete="off"
            />
          </Field>
          <Field name="claimantName" label="Claimant name" error={errors.claimantName}>
            <input
              id="claimantName"
              name="claimantName"
              value={values.claimantName}
              onChange={(event) => update('claimantName', event.target.value)}
              maxLength={200}
              aria-invalid={Boolean(errors.claimantName)}
              aria-describedby={errors.claimantName ? 'claimantName-error' : undefined}
              autoComplete="name"
            />
          </Field>
          <Field name="claimType" label="Claim type" error={errors.claimType}>
            <select
              id="claimType"
              name="claimType"
              value={values.claimType}
              onChange={(event) => update('claimType', event.target.value)}
              aria-invalid={Boolean(errors.claimType)}
              aria-describedby={errors.claimType ? 'claimType-error' : undefined}
            >
              <option value="">Select a claim type</option>
              {CLAIM_TYPES.map((claimType) => (
                <option key={claimType} value={claimType}>
                  {claimType.charAt(0) + claimType.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </Field>
          <Field name="incidentDate" label="Incident date" error={errors.incidentDate}>
            <input
              id="incidentDate"
              type="date"
              name="incidentDate"
              value={values.incidentDate}
              max={today()}
              onChange={(event) => update('incidentDate', event.target.value)}
              aria-invalid={Boolean(errors.incidentDate)}
              aria-describedby={errors.incidentDate ? 'incidentDate-error' : undefined}
            />
          </Field>
          <Field name="estimatedLoss" label="Estimated loss" error={errors.estimatedLoss} hint="Amount; currency is not inferred">
            <input
              id="estimatedLoss"
              name="estimatedLoss"
              value={values.estimatedLoss}
              onChange={(event) => update('estimatedLoss', event.target.value)}
              inputMode="decimal"
              placeholder="0.00"
              aria-invalid={Boolean(errors.estimatedLoss)}
              aria-describedby={errors.estimatedLoss ? 'estimatedLoss-error' : 'estimatedLoss-hint'}
            />
          </Field>
          <Field name="description" label="Incident description" error={errors.description} className="full-field">
            <textarea
              id="description"
              name="description"
              value={values.description}
              onChange={(event) => update('description', event.target.value)}
              maxLength={4_000}
              rows={5}
              aria-invalid={Boolean(errors.description)}
              aria-describedby={errors.description ? 'description-error' : 'description-count'}
            />
            <small id="description-count" className="character-count">
              {values.description.length.toLocaleString()} / 4,000
            </small>
          </Field>
        </div>

        <div className="form-actions">
          <p>All fields are required. Use synthetic data only.</p>
          <div>
            <button type="button" onClick={onCancel} disabled={isSubmitting}>Cancel</button>
            <button className="primary-button" type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Submitting…' : 'Submit claim'}
            </button>
          </div>
        </div>
      </form>
    </section>
  )
}

interface FieldProps {
  name: FormField
  label: string
  error?: string
  hint?: string
  className?: string
  children: ReactNode
}

function Field({ name, label, error, hint, className, children }: FieldProps) {
  return (
    <div className={`form-field ${className ?? ''}`}>
      <label htmlFor={name}>{label}</label>
      {children}
      {hint && !error && <small id={`${name}-hint`}>{hint}</small>}
      {error && <small className="field-error" id={`${name}-error`}>{error}</small>}
    </div>
  )
}
