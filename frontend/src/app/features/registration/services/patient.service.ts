import { Injectable } from '@angular/core';
import type { PatientRecord, PatientRequest, PatientSummary } from '../models/patient.model';
import { PatientRepository } from './patient.repository';
import { validatePatient } from './patient-validation';

@Injectable({ providedIn: 'root' })
export class PatientService {
  constructor(private readonly repository: PatientRepository) {}
  create(request: PatientRequest): Promise<PatientRecord> { return this.repository.create(validatePatient(request)); }
  update(patientNumber: string, request: PatientRequest): Promise<PatientRecord> { return this.repository.update(patientNumber, validatePatient(request)); }
  get(patientNumber: string): Promise<PatientRecord | null> { return this.repository.findByNumber(patientNumber); }
  search(query = ''): Promise<PatientSummary[]> { return this.repository.search(query); }
}
