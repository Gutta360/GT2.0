import { Component, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CLIENT_BRAND, type ModuleKey } from '../../core/configuration/client-brand.config';
import { SyncService } from '../../core/sync/sync.service';
import { SyncPanelComponent } from '../../features/sync/sync-panel.component';

@Component({
  selector: 'app-shell', imports: [RouterOutlet, RouterLink, RouterLinkActive, SyncPanelComponent],
  template: `
    <div class="app-shell" [style.--brand-primary]="brand.primaryColor" [style.--brand-secondary]="brand.secondaryColor">
      <header class="main-header">
        <a class="brand" routerLink="/home"><img [src]="brand.logoPath" alt="" /><span>{{ brand.applicationName }}</span></a>
        <nav class="main-navigation" aria-label="Main navigation">
          @if (isEnabled('home')) { <a class="nav-item home-link" routerLink="/home" routerLinkActive="active" aria-label="Home" (click)="closeMenu()">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10.8 12 3l9 7.8M5.5 9.5V21h13V9.5M9.5 21v-6.5h5V21"/></svg>
          </a> }
          @if (isEnabled('patients')) { <div class="nav-dropdown" [class.open]="openMenu === 'patients'">
            <button class="nav-item nav-toggle" [class.active]="isSection('/patients')" type="button" (click)="toggleMenu('patients')">{{ brand.navigationLabels.patients }}</button>
            <div class="dropdown-menu">
              <a routerLink="/patients/registration" routerLinkActive="active" (click)="closeMenu()">Registration</a>
              <a routerLink="/patients/details" routerLinkActive="active" (click)="closeMenu()">Patient Details</a>
              <a routerLink="/patients/summary" routerLinkActive="active" (click)="closeMenu()">Patient Summary</a>
            </div>
          </div> }
          @if (isEnabled('appointments')) { <a class="nav-item" routerLink="/appointments" routerLinkActive="active" (click)="closeMenu()">{{ brand.navigationLabels.appointments }}</a> }
          @if (isEnabled('treatments')) { <a class="nav-item" routerLink="/treatments" routerLinkActive="active" (click)="closeMenu()">{{ brand.navigationLabels.treatments }}</a> }
          @if (isEnabled('payments')) { <a class="nav-item" routerLink="/payments" routerLinkActive="active" (click)="closeMenu()">{{ brand.navigationLabels.payments }}</a> }
          @if (isEnabled('pharmacy')) { <a class="nav-item" routerLink="/pharmacy" routerLinkActive="active" (click)="closeMenu()">{{ brand.navigationLabels.pharmacy }}</a> }
        </nav>
        <div class="header-actions">
          <button type="button" class="icon-button" aria-label="Sync" title="Sync" (click)="syncPanelOpen.set(true)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7h-5V2M4 17h5v5M19 12a7 7 0 0 0-12-5L4 10M5 12a7 7 0 0 0 12 5l3-3"/></svg>
          </button>
          <button type="button" class="icon-button power" aria-label="Logout" title="Logout" (click)="logout()">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2v10M5.6 5.6a9 9 0 1 0 12.8 0"/></svg>
          </button>
        </div>
      </header>
      <main class="application-content"><router-outlet /></main>
      @if (syncPanelOpen()) { <app-sync-panel (closed)="syncPanelOpen.set(false)" /> }
      @if (toast()) { <div class="toast" role="status">{{ toast() }}</div> }
    </div>`
})
export class AppShellComponent {
  readonly brand = CLIENT_BRAND; openMenu: string | null = null; readonly toast = signal(''); readonly syncPanelOpen = signal(false);
  constructor(private readonly auth: AuthService, private readonly router: Router, private readonly sync: SyncService) { void this.sync.refresh(); }
  toggleMenu(menu: string): void { this.openMenu = this.openMenu === menu ? null : menu; }
  closeMenu(): void { this.openMenu = null; }
  isEnabled(module: ModuleKey): boolean { return this.brand.enabledModules.includes(module); }
  isSection(path: string): boolean { return this.router.url.startsWith(path); }
  async logout(): Promise<void> { this.auth.logout(); await this.router.navigate(['/login']); }
}
