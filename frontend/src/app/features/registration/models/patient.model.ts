export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type ReferredBy = 'DOCTOR' | 'PATIENT' | 'ONLINE' | 'SELF' | 'OTHER';

export interface PatientRequest {
  firstName: string; lastName: string | null; gender: Gender; age: number; mobile: string;
  address: string; referredBy: ReferredBy; doctorName: string | null;
  consultationFee: number; registrationDate: string;
}

export interface PatientRecord extends PatientRequest {
  id: string; patientNumber: string; active: boolean; createdAt: string;
  updatedAt: string; deletedAt: string | null; recordVersion: number;
}

export interface PatientSummary {
  id: string; patientNumber: string; firstName: string; lastName: string | null; mobile: string;
}

export interface PatientRow {
  id: string; patient_number: string; first_name: string; last_name: string | null;
  gender: Gender; age: number; mobile: string; address: string; referred_by: ReferredBy;
  doctor_name: string | null; consultation_fee_cents: number; registration_date: string;
  is_active: number; created_at: string; updated_at: string; deleted_at: string | null; record_version: number;
}
