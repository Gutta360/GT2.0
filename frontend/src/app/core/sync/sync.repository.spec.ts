import { describe, expect, it, vi } from 'vitest';
import type { DatabaseService } from '../database/database.service';
import { SyncRepository } from './sync.repository';

describe('SyncRepository', () => {
  it('selects pending events in order and claims them before upload', async () => {
    const rows = [{ event_id: 'event-1', contract_version: 1, entity_type: 'PATIENT', entity_id: 'patient-1',
      operation: 'CREATE', payload: '{}', entity_version: 1, created_at: '2026-01-01T00:00:00Z' }];
    const connection = { run: vi.fn().mockResolvedValue({}), query: vi.fn().mockResolvedValue({ values: rows }) };
    const database = { requireConnection: () => connection, executeBatch: vi.fn().mockResolvedValue({}) };
    const repository = new SyncRepository(database as unknown as DatabaseService);
    const selected = await repository.claim(100);
    expect(connection.query).toHaveBeenCalledWith(expect.stringContaining("sync_status IN ('PENDING', 'FAILED')"), [100]);
    expect(database.executeBatch).toHaveBeenCalledWith([expect.objectContaining({ values: ['event-1'] })]);
    expect(selected[0]?.eventId).toBe('event-1');
  });

  it('acknowledges only accepted events and preserves rejected events for attention', async () => {
    const database = { requireConnection: vi.fn(), executeBatch: vi.fn().mockResolvedValue({}) };
    const repository = new SyncRepository(database as unknown as DatabaseService);
    const events = [
      { eventId: 'accepted', contractVersion: 1 as const, entityType: 'PATIENT' as const, entityId: 'p1', operation: 'CREATE' as const, payload: '{}', entityVersion: 1, createdAt: 'now' },
      { eventId: 'rejected', contractVersion: 1 as const, entityType: 'PATIENT' as const, entityId: 'p2', operation: 'CREATE' as const, payload: '{}', entityVersion: 1, createdAt: 'now' }
    ];
    await repository.settle(events, [
      { eventId: 'accepted', status: 'ACCEPTED', code: 'APPLIED', message: 'ok', acknowledgedAt: 'then' },
      { eventId: 'rejected', status: 'REJECTED', code: 'INVALID', message: 'bad', acknowledgedAt: 'then' }
    ]);
    const statements = database.executeBatch.mock.calls[0]?.[0] as Array<{ statement: string; values: unknown[] }>;
    expect(statements[0]?.statement).toContain("'ACKNOWLEDGED'");
    expect(statements[1]?.statement).toContain("'FAILED'");
    expect(statements[1]?.values[0]).toBe('INVALID: bad');
  });
});
