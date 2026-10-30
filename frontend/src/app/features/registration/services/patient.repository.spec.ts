import { describe, expect, it, vi } from 'vitest';
import type { DatabaseService } from '../../../core/database/database.service';
import type { PatientRequest } from '../models/patient.model';
import { PatientRepository } from './patient.repository';

describe('PatientRepository', () => {
  it('writes sequence, patient, and outbox in one native transaction batch', async () => {
    const connection = {
      query: vi.fn()
        .mockResolvedValueOnce({ values: [{ next_number: 7 }] })
        .mockResolvedValueOnce({ values: [{ id: 'saved' }] })
    };
    const executeBatch = vi.fn().mockResolvedValue({ changes: { changes: 3 } });
    const database = { requireConnection: () => connection, executeBatch } as unknown as DatabaseService;
    const repository = new PatientRepository(database);
    const input: PatientRequest = {
      firstName: 'Anu', lastName: 'Rao', gender: 'FEMALE', age: 32, mobile: '9876543210',
      address: 'Bengaluru', referredBy: 'SELF', doctorName: null, consultationFee: 500,
      registrationDate: '2026-10-29T06:30:00.000Z'
    };
    const created = await repository.create(input);
    expect(created.patientNumber).toBe('P-00007');
    expect(executeBatch).toHaveBeenCalledOnce();
    const statements = executeBatch.mock.calls[0][0] as Array<{ statement: string }>;
    expect(statements).toHaveLength(3);
    expect(statements.some(value => value.statement.includes('INSERT INTO patients'))).toBe(true);
    expect(statements.some(value => value.statement.includes('INSERT INTO sync_outbox'))).toBe(true);
  });
});
