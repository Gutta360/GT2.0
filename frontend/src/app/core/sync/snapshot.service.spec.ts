import { describe, expect, it, vi } from 'vitest';
import type { DatabaseService } from '../database/database.service';
import type { SyncApiService } from './sync-api.service';
import { SnapshotService } from './snapshot.service';
import type { SyncRepository } from './sync.repository';

const config = { serverUrl: 'https://backup.test', tenantId: 'tenant', deviceId: 'device', deviceToken: 'token',
  deviceName: 'Tablet', registeredAt: 'now', lastSuccessfulSyncAt: 'now', lastAcknowledgedEventId: 'event' };

describe('SnapshotService', () => {
  it('refuses a recovery snapshot until all local changes are acknowledged', async () => {
    const database = { requireConnection: vi.fn() };
    const repository = { configuration: vi.fn().mockResolvedValue(config), pendingCount: vi.fn().mockResolvedValue(1) };
    const api = { uploadSnapshot: vi.fn() };
    const service = new SnapshotService(database as unknown as DatabaseService,
      repository as unknown as SyncRepository, api as unknown as SyncApiService);
    await expect(service.createAndUpload()).rejects.toThrow('Sync all pending patient changes');
    expect(database.requireConnection).not.toHaveBeenCalled();
    expect(api.uploadSnapshot).not.toHaveBeenCalled();
  });

  it('never overwrites a tablet that already contains local patients', async () => {
    const connection = { query: vi.fn().mockResolvedValue({ values: [{ count: 1 }] }) };
    const database = { requireConnection: () => connection, executeBatch: vi.fn() };
    const repository = { configuration: vi.fn().mockResolvedValue(config) };
    const api = { downloadSnapshot: vi.fn(), recordRecovery: vi.fn().mockResolvedValue(undefined) };
    const service = new SnapshotService(database as unknown as DatabaseService,
      repository as unknown as SyncRepository, api as unknown as SyncApiService);
    await expect(service.restore('snapshot')).rejects.toThrow('only when the local patient database is empty');
    expect(api.downloadSnapshot).not.toHaveBeenCalled();
    expect(database.executeBatch).not.toHaveBeenCalled();
  });
});
