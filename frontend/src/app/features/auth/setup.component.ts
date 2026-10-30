import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CLIENT_BRAND } from '../../core/configuration/client-brand.config';
import { DatabaseService } from '../../core/database/database.service';

@Component({
  selector: 'app-setup', imports: [FormsModule],
  template: `
    <main class="auth-page" [style.--brand-primary]="brand.primaryColor" [style.--brand-secondary]="brand.secondaryColor">
      <section class="auth-card">
        <header class="auth-header"><img [src]="brand.logoPath" alt="" /><div><h1>Secure setup</h1><p>First tablet administrator</p></div></header>
        <form (ngSubmit)="enroll()">
          <div class="auth-body">
            <p class="muted">Create the first local account. The password cannot be recovered without a future Phase 2 recovery process.</p>
            <label>Display name<input name="displayName" [(ngModel)]="displayName" maxlength="80" required /></label>
            <label>Username<input name="username" [(ngModel)]="username" minlength="3" maxlength="50" autocomplete="username" required /></label>
            <label>Password<input name="password" type="password" [(ngModel)]="password" minlength="12" autocomplete="new-password" required /></label>
            <label>Confirm password<input name="confirmation" type="password" [(ngModel)]="confirmation" autocomplete="new-password" required /></label>
            <p class="password-note">Use at least 12 characters. A passphrase is recommended.</p>
            @if (message) { <div class="alert" role="alert">{{ message }}</div> }
          </div>
          <footer class="auth-footer"><button class="primary-button" type="submit" [disabled]="busy || database.state() !== 'ready'">{{ busy ? 'Creating…' : 'Create account' }}</button></footer>
        </form>
      </section>
    </main>`
})
export class SetupComponent implements OnInit {
  readonly brand = CLIENT_BRAND;
  displayName = ''; username = ''; password = ''; confirmation = ''; message = ''; busy = false;
  constructor(readonly database: DatabaseService, private readonly auth: AuthService, private readonly router: Router, private readonly changeDetector: ChangeDetectorRef) {}
  async ngOnInit(): Promise<void> {
    if (this.database.state() === 'ready' && await this.auth.hasEnrolledUser()) { await this.router.navigate(['/login']); return; }
    else if (this.database.state() !== 'ready') this.message = this.database.errorMessage();
    this.changeDetector.markForCheck();
  }
  async enroll(): Promise<void> {
    if (this.displayName.trim().length < 2) { this.message = 'Enter a display name.'; return; }
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(this.username.trim())) { this.message = 'Username must be 3–50 letters, numbers, dots, underscores, or hyphens.'; return; }
    if (this.password.length < 12) { this.message = 'Password must contain at least 12 characters.'; return; }
    if (this.password !== this.confirmation) { this.message = 'Passwords do not match.'; return; }
    this.busy = true; this.message = '';
    try { await this.auth.enroll(this.username, this.displayName, this.password); await this.router.navigate(['/login']); }
    catch (error) { this.message = error instanceof Error ? error.message : 'Account creation failed.'; }
    finally { this.busy = false; this.changeDetector.markForCheck(); }
  }
}
