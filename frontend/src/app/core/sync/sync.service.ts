import { Injectable, signal } from '@angular/core';
import type { OutboxEvent } from './outbox.models';
import { SyncApiService } from './sync-api.service';
import { SyncRepository } from './sync.repository';

export type SyncState = 'unconfigured' | 'idle' | 'offline' | 'syncing' | 'success' | 'error';

@Injectable({ providedIn: 'root' })
export class SyncService {
  readonly state = signal<SyncState>('idle');
  readonly pendingCount = signal(0);
  readonly uploadedLastRun = signal(0);
  readonly lastSuccessfulSyncAt = signal<string | null>(null);
  readonly failureMessage = signal('');
  private active: Promise<void> | null = null;

  constructor(private readonly repository: SyncRepository, private readonly api: SyncApiService) {
    window.addEventListener('online', () => { void this.syncNow(); });
  }

  async refresh(): Promise<void> {
    const config = await this.repository.configuration();
    this.state.set(config ? 'idle' : 'unconfigured');
    this.lastSuccessfulSyncAt.set(config?.lastSuccessfulSyncAt ?? null);
    this.pendingCount.set(await this.repository.pendingCount());
  }

  async register(serverUrl: string, enrollmentCode: string, deviceName: string): Promise<void> {
    const normalized = this.normalizeServerUrl(serverUrl);
    this.state.set('syncing'); this.failureMessage.set('');
    try {
      const response = await this.api.register(normalized, enrollmentCode.trim(), deviceName.trim());
      await this.repository.saveRegistration(normalized, deviceName.trim(), response);
      this.state.set('idle');
      await this.syncNow();
    } catch (error) {
      this.state.set('error'); this.failureMessage.set(this.message(error)); throw error;
    }
  }

  syncNow(): Promise<void> {
    if (this.active) return this.active;
    this.active = this.performSync().finally(() => { this.active = null; });
    return this.active;
  }

  private async performSync(): Promise<void> {
    const config = await this.repository.configuration();
    if (!config) { this.state.set('unconfigured'); return; }
    if (!navigator.onLine) { this.state.set('offline'); this.failureMessage.set('No network connection. Local work is safe.'); return; }
    this.state.set('syncing'); this.failureMessage.set(''); this.uploadedLastRun.set(0);
    let events: OutboxEvent[] = [];
    try {
      events = await this.repository.claim(100);
      this.pendingCount.set(await this.repository.pendingCount());
      if (!events.length) { this.state.set('idle'); return; }
      const response = await this.api.uploadEvents(config, events);
      await this.repository.settle(events, response.results);
      const uploaded = response.acceptedCount + response.duplicateCount;
      this.uploadedLastRun.set(uploaded);
      const now = new Date().toISOString();
      await this.repository.recordSuccess(now);
      this.lastSuccessfulSyncAt.set(now);
      this.pendingCount.set(await this.repository.pendingCount());
      if (response.rejectedCount || response.retryableCount) {
        this.state.set('error');
        this.failureMessage.set(`${response.rejectedCount + response.retryableCount} event(s) need attention or retry.`);
      } else this.state.set('success');
    } catch (error) {
      await this.repository.fail(events, this.message(error));
      this.pendingCount.set(await this.repository.pendingCount());
      this.state.set('error'); this.failureMessage.set(this.message(error));
    }
  }

  private normalizeServerUrl(value: string): string {
    const url = new URL(value.trim());
    const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
    if (url.protocol !== 'https:' && !(local && url.protocol === 'http:')) {
      throw new Error('The backup server must use HTTPS. HTTP is allowed only for localhost development.');
    }
    return url.toString().replace(/\/$/, '');
  }

  private message(error: unknown): string {
    if (error && typeof error === 'object' && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return error instanceof Error ? error.message : 'Backup server could not be reached.';
  }
}
