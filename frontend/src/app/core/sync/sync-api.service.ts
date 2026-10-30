import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import type { OutboxEvent, RegistrationResponse, SyncBatchResponse, SyncConfiguration } from './outbox.models';

export interface SnapshotMetadata {
  snapshotId: string; contractVersion: number; localSchemaVersion: number; snapshotCreatedAt: string;
  uploadedAt: string; patientCount: number; lastAcknowledgedEventId: string | null; sha256: string; contentLength: number;
}

@Injectable({ providedIn: 'root' })
export class SyncApiService {
  constructor(private readonly http: HttpClient) {}

  register(serverUrl: string, enrollmentCode: string, deviceName: string): Promise<RegistrationResponse> {
    return firstValueFrom(this.http.post<RegistrationResponse>(`${serverUrl}/api/v1/devices/register`, {
      enrollmentCode, deviceName, applicationId: 'com.gutta.gt2', applicationVersion: '2.0.0', platform: 'ANDROID'
    }).pipe(timeout(15_000)));
  }

  uploadEvents(config: SyncConfiguration, events: OutboxEvent[]): Promise<SyncBatchResponse> {
    return firstValueFrom(this.http.post<SyncBatchResponse>(`${config.serverUrl}/api/v1/sync/events`, {
      contractVersion: 1, deviceId: config.deviceId, batchId: crypto.randomUUID(),
      events: events.map(event => ({ ...event, payload: JSON.parse(event.payload) as unknown }))
    }, { headers: this.auth(config) }).pipe(timeout(30_000)));
  }

  uploadSnapshot(config: SyncConfiguration, snapshot: string, sha256: string): Promise<SnapshotMetadata> {
    return firstValueFrom(this.http.post<SnapshotMetadata>(`${config.serverUrl}/api/v1/snapshots`,
      { snapshot, sha256 }, { headers: this.auth(config) }).pipe(timeout(60_000)));
  }

  listSnapshots(config: SyncConfiguration): Promise<SnapshotMetadata[]> {
    return firstValueFrom(this.http.get<SnapshotMetadata[]>(`${config.serverUrl}/api/v1/snapshots`,
      { headers: this.auth(config) }).pipe(timeout(15_000)));
  }

  async downloadSnapshot(config: SyncConfiguration, snapshotId: string): Promise<{ content: string; sha256: string }> {
    const response = await firstValueFrom(this.http.get(`${config.serverUrl}/api/v1/snapshots/${snapshotId}/recovery-package`, {
      headers: this.auth(config), observe: 'response', responseType: 'text'
    }).pipe(timeout(60_000)));
    return { content: response.body ?? '', sha256: response.headers.get('X-Snapshot-Sha256') ?? '' };
  }

  recordRecovery(config: SyncConfiguration, snapshotId: string, outcome: 'SUCCESS' | 'FAILED', details: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${config.serverUrl}/api/v1/snapshots/${snapshotId}/recovery-audit`,
      { outcome, details }, { headers: this.auth(config) }).pipe(timeout(15_000)));
  }

  private auth(config: SyncConfiguration): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${config.deviceToken}` });
  }
}
