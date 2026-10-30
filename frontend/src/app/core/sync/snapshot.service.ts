import { Injectable } from '@angular/core';
import { DatabaseService } from '../database/database.service';
import type { SqliteStatement } from '../database/database.types';
import type { PatientRecord, PatientRow } from '../../features/registration/models/patient.model';
import { SyncApiService, type SnapshotMetadata } from './sync-api.service';
import { SyncRepository } from './sync.repository';

interface SnapshotDocument {
  contractVersion: 1; localSchemaVersion: 2; createdAt: string;
  lastAcknowledgedEventId: string | null; patients: PatientRecord[];
}

@Injectable({ providedIn: 'root' })
export class SnapshotService {
  constructor(private readonly database: DatabaseService, private readonly repository: SyncRepository,
              private readonly api: SyncApiService) {}

  async createAndUpload(): Promise<SnapshotMetadata> {
    const config = await this.requireConfiguration();
    if (await this.repository.pendingCount() !== 0) {
      throw new Error('Sync all pending patient changes before creating a recovery snapshot.');
    }
    const result = await this.database.requireConnection().query(
      'SELECT * FROM patients ORDER BY patient_number'
    ) as { values?: PatientRow[] };
    const document: SnapshotDocument = {
      contractVersion: 1, localSchemaVersion: 2, createdAt: new Date().toISOString(),
      lastAcknowledgedEventId: config.lastAcknowledgedEventId,
      patients: (result.values ?? []).map(row => this.fromRow(row))
    };
    const content = JSON.stringify(document);
    return this.api.uploadSnapshot(config, content, await this.sha256(content));
  }

  async list(): Promise<SnapshotMetadata[]> {
    return this.api.listSnapshots(await this.requireConfiguration());
  }

  async restore(snapshotId: string): Promise<number> {
    const config = await this.requireConfiguration();
    try {
      const count = await this.database.requireConnection().query('SELECT count(*) AS count FROM patients') as { values?: Array<{ count: number }> };
      if (Number(count.values?.[0]?.count ?? 0) !== 0) {
        throw new Error('Recovery is allowed only when the local patient database is empty.');
      }
      const download = await this.api.downloadSnapshot(config, snapshotId);
      if (!download.sha256 || await this.sha256(download.content) !== download.sha256) {
        throw new Error('Downloaded snapshot checksum verification failed.');
      }
      const document = JSON.parse(download.content) as SnapshotDocument;
      if (document.contractVersion !== 1 || document.localSchemaVersion !== 2 || !Array.isArray(document.patients)) {
        throw new Error('Snapshot contract or local schema version is unsupported.');
      }
      const statements: SqliteStatement[] = document.patients.map(patient => ({
        statement: `INSERT INTO patients(id, patient_number, first_name, last_name, gender, age, mobile, address,
          referred_by, doctor_name, consultation_fee_cents, registration_date, is_active, created_at, updated_at,
          deleted_at, record_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        values: [patient.id, patient.patientNumber, patient.firstName, patient.lastName, patient.gender, patient.age,
          patient.mobile, patient.address, patient.referredBy, patient.doctorName,
          Math.round(patient.consultationFee * 100), patient.registrationDate, patient.active ? 1 : 0,
          patient.createdAt, patient.updatedAt, patient.deletedAt, patient.recordVersion]
      }));
      const highest = document.patients.reduce((max, patient) =>
        Math.max(max, Number(patient.patientNumber.replace(/^P-/, '')) || 0), 0);
      statements.push({
        statement: 'UPDATE patient_number_sequence SET next_number = ? WHERE singleton = 1',
        values: [highest + 1]
      });
      await this.database.executeBatch(statements);
      await this.api.recordRecovery(config, snapshotId, 'SUCCESS', `Restored ${document.patients.length} patient records.`);
      return document.patients.length;
    } catch (error) {
      try { await this.api.recordRecovery(config, snapshotId, 'FAILED', this.message(error)); } catch { /* preserve the original failure */ }
      throw error;
    }
  }

  private async requireConfiguration() {
    const config = await this.repository.configuration();
    if (!config) throw new Error('Register this tablet with the backup server first.');
    return config;
  }

  private async sha256(value: string): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
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

  private message(error: unknown): string { return error instanceof Error ? error.message.slice(0, 500) : 'Recovery failed.'; }
}
