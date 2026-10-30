import { Injectable } from '@angular/core';
import { DatabaseService } from '../database/database.service';
import type { SqliteStatement } from '../database/database.types';
import type { OutboxEvent, RegistrationResponse, SyncConfiguration, SyncEventResponse } from './outbox.models';

interface OutboxRow {
  event_id: string; contract_version: 1; entity_type: 'PATIENT'; entity_id: string;
  operation: 'CREATE' | 'UPDATE' | 'DELETE'; payload: string; entity_version: number; created_at: string;
}

@Injectable({ providedIn: 'root' })
export class SyncRepository {
  constructor(private readonly database: DatabaseService) {}

  async configuration(): Promise<SyncConfiguration | null> {
    const result = await this.database.requireConnection().query(
      'SELECT * FROM sync_configuration WHERE singleton = 1'
    ) as { values?: Array<Record<string, string | null>> };
    const row = result.values?.[0];
    return row ? {
      serverUrl: String(row['server_url']), tenantId: String(row['tenant_id']), deviceId: String(row['device_id']),
      deviceToken: String(row['device_token']), deviceName: String(row['device_name']), registeredAt: String(row['registered_at']),
      lastSuccessfulSyncAt: row['last_successful_sync_at'], lastAcknowledgedEventId: row['last_acknowledged_event_id']
    } : null;
  }

  async saveRegistration(serverUrl: string, deviceName: string, response: RegistrationResponse): Promise<void> {
    await this.database.requireConnection().run(
      `INSERT OR REPLACE INTO sync_configuration(singleton, server_url, tenant_id, device_id, device_token,
       device_name, registered_at, last_successful_sync_at, last_acknowledged_event_id)
       VALUES (1, ?, ?, ?, ?, ?, ?, NULL, NULL)`,
      [serverUrl, response.tenantId, response.deviceId, response.deviceToken, deviceName, response.registeredAt], true
    );
  }

  async pendingCount(): Promise<number> {
    const result = await this.database.requireConnection().query(
      "SELECT count(*) AS count FROM sync_outbox WHERE sync_status <> 'ACKNOWLEDGED'"
    ) as { values?: Array<{ count: number }> };
    return Number(result.values?.[0]?.count ?? 0);
  }

  async claim(limit: number): Promise<OutboxEvent[]> {
    const db = this.database.requireConnection();
    await db.run("UPDATE sync_outbox SET sync_status = 'FAILED', last_error = 'Interrupted before acknowledgement' WHERE sync_status = 'IN_FLIGHT'", [], true);
    const result = await db.query(
      `SELECT event_id, contract_version, entity_type, entity_id, operation, payload, entity_version, created_at
       FROM sync_outbox WHERE sync_status IN ('PENDING', 'FAILED') ORDER BY created_at, event_id LIMIT ?`, [limit]
    ) as { values?: OutboxRow[] };
    const rows = result.values ?? [];
    if (!rows.length) return [];
    await this.database.executeBatch(rows.map(row => ({
      statement: "UPDATE sync_outbox SET sync_status = 'IN_FLIGHT', attempt_count = attempt_count + 1, last_error = NULL WHERE event_id = ?",
      values: [row.event_id]
    })));
    return rows.map(row => ({ eventId: row.event_id, contractVersion: row.contract_version,
      entityType: row.entity_type, entityId: row.entity_id, operation: row.operation,
      payload: row.payload, entityVersion: row.entity_version, createdAt: row.created_at }));
  }

  async settle(events: OutboxEvent[], responses: SyncEventResponse[]): Promise<void> {
    const byId = new Map(responses.map(response => [response.eventId, response]));
    const statements: SqliteStatement[] = events.map(event => {
      const response = byId.get(event.eventId);
      const accepted = response?.status === 'ACCEPTED' || response?.status === 'ALREADY_PROCESSED';
      return accepted ? {
        statement: "UPDATE sync_outbox SET sync_status = 'ACKNOWLEDGED', acknowledged_at = ?, last_error = NULL WHERE event_id = ?",
        values: [response.acknowledgedAt, event.eventId]
      } : {
        statement: "UPDATE sync_outbox SET sync_status = 'FAILED', last_error = ? WHERE event_id = ?",
        values: [response ? `${response.code}: ${response.message}` : 'Server response omitted this event', event.eventId]
      };
    });
    const acknowledged = responses.filter(item => item.status === 'ACCEPTED' || item.status === 'ALREADY_PROCESSED').at(-1);
    if (acknowledged) statements.push({
      statement: 'UPDATE sync_configuration SET last_acknowledged_event_id = ? WHERE singleton = 1',
      values: [acknowledged.eventId]
    });
    await this.database.executeBatch(statements);
  }

  async fail(events: OutboxEvent[], message: string): Promise<void> {
    if (!events.length) return;
    await this.database.executeBatch(events.map(event => ({
      statement: "UPDATE sync_outbox SET sync_status = 'FAILED', last_error = ? WHERE event_id = ?",
      values: [message.slice(0, 500), event.eventId]
    })));
  }

  async recordSuccess(at: string): Promise<void> {
    await this.database.requireConnection().run(
      'UPDATE sync_configuration SET last_successful_sync_at = ? WHERE singleton = 1', [at], true
    );
  }
}
