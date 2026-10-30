import { Injectable, signal } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { CapacitorSQLite, SQLiteConnection, type SQLiteDBConnection } from '@capacitor-community/sqlite';
import { CURRENT_SCHEMA, DATABASE_VERSION, SCHEMA_V2_STATEMENTS } from './migrations';
import type { ChangeResult, DatabaseState, SqliteStatement } from './database.types';

@Injectable({ providedIn: 'root' })
export class DatabaseService {
  private readonly sqlite = new SQLiteConnection(CapacitorSQLite);
  private connection: SQLiteDBConnection | null = null;
  readonly state = signal<DatabaseState>('initializing');
  readonly errorMessage = signal('');

  async initialize(): Promise<void> {
    if (!Capacitor.isNativePlatform()) {
      this.state.set('unsupported');
      this.errorMessage.set('GT2.0 business data requires the native Android SQLite runtime.');
      return;
    }
    try {
      const stored = await this.sqlite.isSecretStored();
      if (!stored.result) await this.sqlite.setEncryptionSecret(this.generateSecret());
      await this.sqlite.addUpgradeStatement('gt2_operational', [
        { toVersion: 2, statements: SCHEMA_V2_STATEMENTS }
      ]);
      this.connection = await this.sqlite.createConnection('gt2_operational', true, 'secret', DATABASE_VERSION, false);
      await this.connection.open();
      await this.connection.execute(CURRENT_SCHEMA, false);
      await this.connection.run(
        'INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (?, ?)',
        [DATABASE_VERSION, new Date().toISOString()], false
      );
      this.state.set('ready');
    } catch (error) {
      this.state.set('error');
      this.errorMessage.set('The encrypted local database could not be opened.');
      throw error;
    }
  }

  requireConnection(): SQLiteDBConnection {
    if (!this.connection || this.state() !== 'ready') throw new Error(this.errorMessage() || 'Database is not ready.');
    return this.connection;
  }

  /**
   * Runs the complete write set inside one native bridge call. Android SQLite
   * transactions are thread-affine, so begin/work/commit must not be split
   * across separate Capacitor calls.
   */
  async executeBatch(statements: SqliteStatement[]): Promise<ChangeResult> {
    const db = this.requireConnection();
    return db.executeSet(statements, true) as Promise<ChangeResult>;
  }

  private generateSecret(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  }
}
