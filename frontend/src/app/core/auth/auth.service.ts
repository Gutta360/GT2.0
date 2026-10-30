import { Injectable, computed, signal } from '@angular/core';
import { DatabaseService } from '../database/database.service';
import { CryptoService } from './crypto.service';
import type { LocalUser, LocalUserRow } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly user = signal<LocalUser | null>(null);
  readonly currentUser = this.user.asReadonly();
  readonly authenticated = computed(() => this.user() !== null);

  constructor(private readonly database: DatabaseService, private readonly cryptoService: CryptoService) {}

  async hasEnrolledUser(): Promise<boolean> {
    const result = await this.database.requireConnection().query('SELECT COUNT(*) AS count FROM local_users WHERE enabled = 1') as { values?: { count: number }[] };
    return Number(result.values?.[0]?.count ?? 0) > 0;
  }

  async enroll(username: string, displayName: string, password: string): Promise<void> {
    if (await this.hasEnrolledUser()) throw new Error('Initial enrollment has already been completed.');
    const digest = await this.cryptoService.hashPassword(password);
    const now = new Date().toISOString();
    await this.database.requireConnection().run(
      `INSERT INTO local_users(id, username, display_name, password_salt, password_hash, password_iterations, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [crypto.randomUUID(), username.trim(), displayName.trim(), digest.salt, digest.hash, digest.iterations, now, now], false
    );
  }

  async login(username: string, password: string): Promise<boolean> {
    const result = await this.database.requireConnection().query(
      `SELECT id, username, display_name, password_salt, password_hash, password_iterations, enabled
       FROM local_users WHERE username = ? COLLATE NOCASE LIMIT 1`, [username.trim()]
    ) as { values?: LocalUserRow[] };
    const row = result.values?.[0];
    if (!row || row.enabled !== 1) return false;
    const valid = await this.cryptoService.verify(password, {
      salt: row.password_salt, hash: row.password_hash, iterations: row.password_iterations
    });
    if (!valid) return false;
    this.user.set({ id: row.id, username: row.username, displayName: row.display_name });
    const now = new Date().toISOString();
    await this.database.requireConnection().run(
      'UPDATE local_users SET last_login_at = ?, updated_at = ? WHERE id = ?', [now, now, row.id], false
    );
    return true;
  }

  logout(): void { this.user.set(null); }
}
