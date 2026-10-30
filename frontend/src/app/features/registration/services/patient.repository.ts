import { Injectable } from '@angular/core';
import { DatabaseService } from '../../../core/database/database.service';
import type { OutboxOperation } from '../../../core/sync/outbox.models';
import type { PatientRecord, PatientRequest, PatientRow, PatientSummary } from '../models/patient.model';
import { DuplicatePatientError } from './patient-validation';

@Injectable({ providedIn: 'root' })
export class PatientRepository {
  constructor(private readonly database: DatabaseService) {}

  async create(input: PatientRequest): Promise<PatientRecord> {
    try {
      const db = this.database.requireConnection();
      const sequence = await db.query('SELECT next_number FROM patient_number_sequence WHERE singleton = 1') as { values?: Array<{ next_number: number }> };
      const next = Number(sequence.values?.[0]?.next_number);
      if (!next) throw new Error('Patient number sequence is unavailable.');
      const now = new Date().toISOString();
      const record: PatientRecord = {
        ...input, id: crypto.randomUUID(), patientNumber: `P-${String(next).padStart(5, '0')}`,
        active: true, createdAt: now, updatedAt: now, deletedAt: null, recordVersion: 1
      };
      const outbox = this.outboxStatement(record, 'CREATE', true);
      await this.database.executeBatch([
        {
          statement: 'UPDATE patient_number_sequence SET next_number = next_number + 1 WHERE singleton = 1 AND next_number = ?',
          values: [next]
        },
        {
          statement: `INSERT INTO patients(id, patient_number, first_name, last_name, gender, age, mobile, address,
            referred_by, doctor_name, consultation_fee_cents, registration_date, created_at, updated_at, record_version)
           SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE changes() = 1`,
          values: [record.id, record.patientNumber, record.firstName, record.lastName, record.gender, record.age, record.mobile,
            record.address, record.referredBy, record.doctorName, Math.round(record.consultationFee * 100),
            record.registrationDate, now, now, record.recordVersion]
        },
        outbox
      ]);
      const persisted = await db.query('SELECT id FROM patients WHERE id = ?', [record.id]) as { values?: Array<{ id: string }> };
      if (!persisted.values?.[0]) throw new Error('Patient number changed in another operation. Try again.');
      return record;
    } catch (error) { throw this.mapWriteError(error); }
  }

  async update(patientNumber: string, input: PatientRequest): Promise<PatientRecord> {
    try {
      const db = this.database.requireConnection();
      const found = await db.query('SELECT * FROM patients WHERE patient_number = ? AND is_active = 1', [patientNumber]) as { values?: PatientRow[] };
      const current = found.values?.[0];
      if (!current) throw new Error('Patient was not found.');
      const now = new Date().toISOString();
      const record: PatientRecord = {
        ...input, id: current.id, patientNumber: current.patient_number, active: true,
        createdAt: current.created_at, updatedAt: now, deletedAt: null, recordVersion: current.record_version + 1
      };
      await this.database.executeBatch([
        {
          statement: `UPDATE patients SET first_name = ?, last_name = ?, gender = ?, age = ?, mobile = ?, address = ?,
            referred_by = ?, doctor_name = ?, consultation_fee_cents = ?, registration_date = ?,
            updated_at = ?, record_version = ? WHERE id = ? AND record_version = ?`,
          values: [record.firstName, record.lastName, record.gender, record.age, record.mobile, record.address,
            record.referredBy, record.doctorName, Math.round(record.consultationFee * 100), record.registrationDate,
            now, record.recordVersion, record.id, current.record_version]
        },
        this.outboxStatement(record, 'UPDATE', true)
      ]);
      const persisted = await db.query('SELECT record_version FROM patients WHERE id = ?', [record.id]) as { values?: Array<{ record_version: number }> };
      if (persisted.values?.[0]?.record_version !== record.recordVersion) {
        throw new Error('Patient changed in another operation. Reload and try again.');
      }
      return record;
    } catch (error) { throw this.mapWriteError(error); }
  }

  async delete(patientNumber: string): Promise<void> {
    const db = this.database.requireConnection();
    const found = await db.query('SELECT * FROM patients WHERE patient_number = ? AND is_active = 1', [patientNumber]) as { values?: PatientRow[] };
    const row = found.values?.[0];
    if (!row) throw new Error('Patient was not found.');
    const record = this.fromRow(row); const now = new Date().toISOString();
    record.active = false; record.deletedAt = now; record.updatedAt = now; record.recordVersion++;
    await this.database.executeBatch([
      {
        statement: 'UPDATE patients SET is_active = 0, deleted_at = ?, updated_at = ?, record_version = ? WHERE id = ? AND record_version = ?',
        values: [now, now, record.recordVersion, record.id, row.record_version]
      },
      this.outboxStatement(record, 'DELETE', true)
    ]);
    const persisted = await db.query('SELECT is_active, record_version FROM patients WHERE id = ?', [record.id]) as { values?: Array<{ is_active: number; record_version: number }> };
    if (persisted.values?.[0]?.is_active !== 0 || persisted.values[0].record_version !== record.recordVersion) {
      throw new Error('Patient changed in another operation. Reload and try again.');
    }
  }

  async findByNumber(patientNumber: string): Promise<PatientRecord | null> {
    const result = await this.database.requireConnection().query('SELECT * FROM patients WHERE patient_number = ? AND is_active = 1', [patientNumber.trim().toUpperCase()]) as { values?: PatientRow[] };
    return result.values?.[0] ? this.fromRow(result.values[0]) : null;
  }

  async search(query = ''): Promise<PatientSummary[]> {
    const normalized = query.trim().toLowerCase();
    const like = `%${normalized}%`;
    const result = await this.database.requireConnection().query(
      `SELECT * FROM patients WHERE is_active = 1 AND
       (? = '' OR lower(patient_number) LIKE ? OR lower(first_name || ' ' || coalesce(last_name, '')) LIKE ? OR mobile LIKE ?)
       ORDER BY first_name, last_name, patient_number LIMIT 100`, [normalized, like, like, like]
    ) as { values?: PatientRow[] };
    return (result.values ?? []).map(row => ({
      id: row.id, patientNumber: row.patient_number, firstName: row.first_name, lastName: row.last_name, mobile: row.mobile
    }));
  }

  private outboxStatement(record: PatientRecord, operation: OutboxOperation, requirePreviousChange: boolean) {
    const eventId = crypto.randomUUID();
    const payload = JSON.stringify({ contractVersion: 1, entityType: 'PATIENT', operation, patient: record });
    return {
      statement: `INSERT INTO sync_outbox(event_id, contract_version, entity_type, entity_id, operation, payload, entity_version, created_at)
       SELECT ?, 1, 'PATIENT', ?, ?, ?, ?, ?${requirePreviousChange ? ' WHERE changes() = 1' : ''}`,
      values: [eventId, record.id, operation, payload, record.recordVersion, record.updatedAt]
    };
  }

  private fromRow(row: PatientRow): PatientRecord {
    return {
      id: row.id, patientNumber: row.patient_number, firstName: row.first_name, lastName: row.last_name,
      gender: row.gender, age: row.age, mobile: row.mobile, address: row.address, referredBy: row.referred_by,
      doctorName: row.doctor_name, consultationFee: row.consultation_fee_cents / 100,
      registrationDate: row.registration_date, active: row.is_active === 1, createdAt: row.created_at,
      updatedAt: row.updated_at, deletedAt: row.deleted_at, recordVersion: row.record_version
    };
  }

  private mapWriteError(error: unknown): Error {
    const message = error instanceof Error ? error.message : String(error);
    if (/UNIQUE constraint failed.*first_name|ux_patients_name_mobile/i.test(message)) {
      return new DuplicatePatientError('A patient with the same first name and mobile number already exists.');
    }
    return error instanceof Error ? error : new Error(message);
  }
}
