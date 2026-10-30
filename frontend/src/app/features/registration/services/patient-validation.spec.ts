import { describe, expect, it } from 'vitest';
import type { PatientRequest } from '../models/patient.model';
import { PatientValidationError, validatePatient } from './patient-validation';

const valid: PatientRequest = {
  firstName: '  Anu  ', lastName: ' Rao ', gender: 'FEMALE', age: 32, mobile: '9876543210',
  address: 'Bengaluru', referredBy: 'SELF', doctorName: 'discarded', consultationFee: 500,
  registrationDate: '2026-10-29T06:30:00.000Z'
};

describe('patient validation', () => {
  it('normalizes names and clears irrelevant doctor details', () => {
    const result = validatePatient(valid);
    expect(result.firstName).toBe('Anu');
    expect(result.lastName).toBe('Rao');
    expect(result.doctorName).toBeNull();
  });

  it('requires doctor name for doctor referrals', () => {
    expect(() => validatePatient({ ...valid, referredBy: 'DOCTOR', doctorName: null })).toThrow(PatientValidationError);
  });

  it('rejects invalid mobile and fee values', () => {
    expect(() => validatePatient({ ...valid, mobile: '123' })).toThrow('exactly 10 digits');
    expect(() => validatePatient({ ...valid, consultationFee: 1.234 })).toThrow('2 decimals');
  });
});
