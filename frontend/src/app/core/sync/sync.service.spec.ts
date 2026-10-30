import { describe, expect, it, vi } from 'vitest';
import type { OutboxEvent, SyncBatchResponse, SyncConfiguration } from './outbox.models';
import { SyncService } from './sync.service';
import type { SyncApiService } from './sync-api.service';
import type { SyncRepository } from './sync.repository';

const configuration: SyncConfiguration = {
  serverUrl: 'https://backup.example.test', tenantId: crypto.randomUUID(), deviceId: crypto.randomUUID(),
  deviceToken: 'token', deviceName: 'Tablet', registeredAt: new Date().toISOString(),
  lastSuccessfulSyncAt: null, lastAcknowledgedEventId: null
};
const event: OutboxEvent = {
  eventId: crypto.randomUUID(), contractVersion: 1, entityType: 'PATIENT', entityId: crypto.randomUUID(),
  operation: 'CREATE', payload: '{}', entityVersion: 1, createdAt: new Date().toISOString()
};

function response(): SyncBatchResponse {
  return { batchId: crypto.randomUUID(), receivedAt: new Date().toISOString(), completedAt: new Date().toISOString(),
    acceptedCount: 1, duplicateCount: 0, rejectedCount: 0, retryableCount: 0,
    results: [{ eventId: event.eventId, status: 'ACCEPTED', code: 'APPLIED', message: 'ok', acknowledgedAt: new Date().toISOString() }] };
}

describe('SyncService', () => {
  it('uploads a claimed batch and records explicit acknowledgement success', async () => {
    const repository = {
      configuration: vi.fn().mockResolvedValue(configuration), claim: vi.fn().mockResolvedValue([event]),
      pendingCount: vi.fn().mockResolvedValueOnce(1).mockResolvedValueOnce(0), settle: vi.fn().mockResolvedValue(undefined),
      recordSuccess: vi.fn().mockResolvedValue(undefined), fail: vi.fn()
    };
    const api = { uploadEvents: vi.fn().mockResolvedValue(response()) };
    const service = new SyncService(repository as unknown as SyncRepository, api as unknown as SyncApiService);
    await service.syncNow();
    expect(api.uploadEvents).toHaveBeenCalledWith(configuration, [event]);
    expect(repository.settle).toHaveBeenCalledOnce();
    expect(service.pendingCount()).toBe(0);
    expect(service.state()).toBe('success');
  });

  it('prevents overlapping duplicate sync attempts', async () => {
    let release!: (value: SyncBatchResponse) => void;
    const pending = new Promise<SyncBatchResponse>(resolve => { release = resolve; });
    const repository = {
      configuration: vi.fn().mockResolvedValue(configuration), claim: vi.fn().mockResolvedValue([event]),
      pendingCount: vi.fn().mockResolvedValue(1), settle: vi.fn(), recordSuccess: vi.fn(), fail: vi.fn()
    };
    const api = { uploadEvents: vi.fn().mockReturnValue(pending) };
    const service = new SyncService(repository as unknown as SyncRepository, api as unknown as SyncApiService);
    const first = service.syncNow(); const second = service.syncNow();
    expect(first).toBe(second);
    release(response()); await first;
    expect(api.uploadEvents).toHaveBeenCalledOnce();
  });

  it('returns claimed events to failed state when the network upload breaks', async () => {
    const repository = {
      configuration: vi.fn().mockResolvedValue(configuration), claim: vi.fn().mockResolvedValue([event]),
      pendingCount: vi.fn().mockResolvedValue(1), settle: vi.fn(), recordSuccess: vi.fn(),
      fail: vi.fn().mockResolvedValue(undefined)
    };
    const api = { uploadEvents: vi.fn().mockRejectedValue(new Error('connection interrupted')) };
    const service = new SyncService(repository as unknown as SyncRepository, api as unknown as SyncApiService);
    await service.syncNow();
    expect(repository.fail).toHaveBeenCalledWith([event], 'connection interrupted');
    expect(service.state()).toBe('error');
  });

  it('does not touch local data while offline', async () => {
    const original = navigator.onLine;
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false });
    const repository = { configuration: vi.fn().mockResolvedValue(configuration), claim: vi.fn() };
    const service = new SyncService(repository as unknown as SyncRepository, {} as SyncApiService);
    await service.syncNow();
    expect(repository.claim).not.toHaveBeenCalled();
    expect(service.state()).toBe('offline');
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: original });
  });
});
