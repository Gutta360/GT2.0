export type OutboxOperation = 'CREATE' | 'UPDATE' | 'DELETE';
export interface OutboxEvent {
  eventId: string; contractVersion: 1; entityType: 'PATIENT'; entityId: string;
  operation: OutboxOperation; payload: string; entityVersion: number; createdAt: string;
}
export type SyncEventStatus = 'ACCEPTED' | 'ALREADY_PROCESSED' | 'REJECTED' | 'RETRYABLE_FAILURE';
export interface SyncEventResponse {
  eventId: string; status: SyncEventStatus; code: string; message: string; acknowledgedAt: string;
}
export interface SyncBatchResponse {
  batchId: string; receivedAt: string; completedAt: string;
  acceptedCount: number; duplicateCount: number; rejectedCount: number; retryableCount: number;
  results: SyncEventResponse[];
}
export interface SyncConfiguration {
  serverUrl: string; tenantId: string; deviceId: string; deviceToken: string;
  deviceName: string; registeredAt: string; lastSuccessfulSyncAt: string | null;
  lastAcknowledgedEventId: string | null;
}
export interface RegistrationResponse {
  tenantId: string; deviceId: string; tokenType: 'Bearer'; deviceToken: string;
  tenantCode: string; tenantName: string; registeredAt: string;
}
