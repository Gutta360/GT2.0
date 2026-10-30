import type { Gender, PatientRequest, ReferredBy } from '../models/patient.model';

export class PatientValidationError extends Error {}
export class DuplicatePatientError extends Error {}

const genders: Gender[] = ['MALE', 'FEMALE', 'OTHER'];
const referrals: ReferredBy[] = ['DOCTOR', 'PATIENT', 'ONLINE', 'SELF', 'OTHER'];

export function validatePatient(input: PatientRequest): PatientRequest {
  const firstName = input.firstName.trim().replace(/\s+/g, ' ');
  const lastName = input.lastName?.trim().replace(/\s+/g, ' ') || null;
  const address = input.address.trim();
  const mobile = input.mobile.trim();
  const doctorName = input.referredBy === 'DOCTOR' ? input.doctorName?.trim() || null : null;

  if (!/^[A-Za-z ]{2,50}$/.test(firstName)) throw new PatientValidationError('First name must be 2–50 letters and spaces.');
  if (lastName && !/^[A-Za-z ]{1,50}$/.test(lastName)) throw new PatientValidationError('Last name may contain up to 50 letters and spaces.');
  if (!genders.includes(input.gender)) throw new PatientValidationError('Select a valid gender.');
  if (!Number.isInteger(input.age) || input.age < 1 || input.age > 120) throw new PatientValidationError('Age must be between 1 and 120.');
  if (!/^\d{10}$/.test(mobile)) throw new PatientValidationError('Mobile number must contain exactly 10 digits.');
  if (address.length < 2 || address.length > 255) throw new PatientValidationError('Address must be 2–255 characters.');
  if (!referrals.includes(input.referredBy)) throw new PatientValidationError('Select a valid referral source.');
  if (input.referredBy === 'DOCTOR' && (!doctorName || doctorName.length < 2 || doctorName.length > 100)) {
    throw new PatientValidationError('Doctor name is required and must be 2–100 characters.');
  }
  if (!Number.isFinite(input.consultationFee) || input.consultationFee < 0 || input.consultationFee > 99_999_999.99 || Math.round(input.consultationFee * 100) !== input.consultationFee * 100) {
    throw new PatientValidationError('Consultation fee must be non-negative with at most 8 integer digits and 2 decimals.');
  }
  if (Number.isNaN(Date.parse(input.registrationDate))) throw new PatientValidationError('Registration date is invalid.');

  return { ...input, firstName, lastName, mobile, address, doctorName, registrationDate: new Date(input.registrationDate).toISOString() };
}
