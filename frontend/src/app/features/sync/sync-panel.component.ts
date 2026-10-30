import { Component, EventEmitter, OnInit, Output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { SnapshotService } from '../../core/sync/snapshot.service';
import { type SnapshotMetadata } from '../../core/sync/sync-api.service';
import { SyncService } from '../../core/sync/sync.service';

@Component({
  selector: 'app-sync-panel',
  imports: [DatePipe],
  template: `
    <div class="sync-backdrop" (click)="closed.emit()"></div>
    <aside class="sync-panel" role="dialog" aria-modal="true" aria-labelledby="sync-title">
      <header><div><span class="eyebrow">Backup & recovery</span><h2 id="sync-title">Tablet sync</h2></div>
        <button class="panel-close" type="button" aria-label="Close" (click)="closed.emit()">×</button></header>

      @if (sync.state() === 'unconfigured') {
        <section class="sync-section">
          <p class="muted">Register this tablet with a one-use enrollment code from the GT2.0 administrator.</p>
          <label>HTTPS server URL <input #serverUrl type="url" placeholder="https://backup.example.com" autocomplete="url" /></label>
          <label>Enrollment code <input #code type="text" placeholder="GT2E_…" autocomplete="off" /></label>
          <label>Device name <input #deviceName type="text" value="GT2.0 11-inch tablet" maxlength="120" /></label>
          <button class="primary-button" type="button" [disabled]="working()"
            (click)="register(serverUrl.value, code.value, deviceName.value)">Register tablet</button>
        </section>
      } @else {
        <section class="sync-section sync-summary">
          <div><span>Status</span><strong>{{ statusLabel() }}</strong></div>
          <div><span>Pending events</span><strong>{{ sync.pendingCount() }}</strong></div>
          <div><span>Uploaded last run</span><strong>{{ sync.uploadedLastRun() }}</strong></div>
          <div><span>Last success</span><strong>{{ sync.lastSuccessfulSyncAt() ? (sync.lastSuccessfulSyncAt() | date:'medium') : 'Not yet' }}</strong></div>
          <button class="primary-button" type="button" [disabled]="working() || sync.state() === 'syncing'" (click)="syncNow()">Sync now</button>
        </section>

        <section class="sync-section">
          <div class="section-heading"><div><span class="eyebrow">Clean-device protection</span><h3>Snapshots</h3></div>
            <button class="secondary-button" type="button" [disabled]="working()" (click)="createSnapshot()">Create snapshot</button></div>
          <p class="muted snapshot-note">Snapshots contain patient records only. Local login credentials and the device token are never included.</p>
          @if (!snapshots().length) { <p class="empty-snapshots">No server snapshots available.</p> }
          @for (snapshot of snapshots(); track snapshot.snapshotId) {
            <div class="snapshot-row"><div><strong>{{ snapshot.snapshotCreatedAt | date:'medium' }}</strong>
              <small>{{ snapshot.patientCount }} patients · {{ snapshot.contentLength }} bytes</small></div>
              <button class="secondary-button" type="button" [disabled]="working()" (click)="restore(snapshot)">Restore</button></div>
          }
        </section>
      }
      @if (sync.failureMessage()) { <p class="panel-alert">{{ sync.failureMessage() }}</p> }
      @if (message()) { <p class="panel-message" role="status">{{ message() }}</p> }
    </aside>`
})
export class SyncPanelComponent implements OnInit {
  @Output() readonly closed = new EventEmitter<void>();
  readonly snapshots = signal<SnapshotMetadata[]>([]);
  readonly working = signal(false);
  readonly message = signal('');

  constructor(readonly sync: SyncService, private readonly snapshotService: SnapshotService) {}

  async ngOnInit(): Promise<void> {
    await this.sync.refresh();
    if (this.sync.state() !== 'unconfigured') await this.loadSnapshots();
  }

  statusLabel(): string {
    return ({ idle: 'Ready', offline: 'Offline', syncing: 'Syncing…', success: 'Up to date',
      error: 'Attention needed', unconfigured: 'Not registered' } as const)[this.sync.state()];
  }

  async register(serverUrl: string, code: string, deviceName: string): Promise<void> {
    if (!serverUrl.trim() || !code.trim() || !deviceName.trim()) { this.message.set('Complete all registration fields.'); return; }
    await this.run(async () => { await this.sync.register(serverUrl, code, deviceName); await this.loadSnapshots(); this.message.set('Tablet registered.'); });
  }

  async syncNow(): Promise<void> {
    await this.run(async () => { await this.sync.syncNow(); this.message.set('Sync attempt completed.'); });
  }

  async createSnapshot(): Promise<void> {
    await this.run(async () => {
      const created = await this.snapshotService.createAndUpload();
      this.message.set(`Snapshot saved with ${created.patientCount} patient records.`); await this.loadSnapshots();
    });
  }

  async restore(snapshot: SnapshotMetadata): Promise<void> {
    if (!confirm('Restore this snapshot? Recovery is allowed only when this tablet has no local patient records.')) return;
    await this.run(async () => {
      const count = await this.snapshotService.restore(snapshot.snapshotId);
      this.message.set(`Recovery completed: ${count} patient records restored.`);
    });
  }

  private async loadSnapshots(): Promise<void> { this.snapshots.set(await this.snapshotService.list()); }

  private async run(action: () => Promise<void>): Promise<void> {
    this.working.set(true); this.message.set('');
    try { await action(); } catch (error) { this.message.set(error instanceof Error ? error.message : 'Operation failed.'); }
    finally { this.working.set(false); }
  }
}
