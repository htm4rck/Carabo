import { Component } from '@angular/core';
import { ToggleService } from './toggle.service';
import { NgClass } from '@angular/common';
import { AuthService } from '../../auth/auth.service';
import { BuildInfoService } from '../../services/build-info.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [NgClass],
  template: `
    <header class="header-area bg-white border-bottom-radius box-shadow" [ngClass]="{'active': isToggled}">
      <div class="d-md-flex align-items-center justify-content-space-between">
        <div class="header-left-side d-flex align-items-center">
          <div class="burger-menu" (click)="toggle()" [ngClass]="{'active': isToggled}">
            <span class="top-bar"></span>
            <span class="middle-bar"></span>
            <span class="bottom-bar"></span>
          </div>
          <div class="context-title"><span>Operación fiscal</span><small>{{ activeRole }}</small></div>
        </div>
        <ul class="header-right-side d-flex align-items-center mt-0 mb-0">
          <li class="version-cluster" aria-label="Versiones desplegadas">
            <span title="UI · fecha de construcción · revisión">UI {{ buildInfo.ui.version }} · {{ buildInfo.ui.buildDate }} · {{ shortCommit(buildInfo.ui.commit) }}</span>
            <span title="API · fecha de construcción · revisión">API {{ buildInfo.api().version }} · {{ buildInfo.api().buildDate || 'sin fecha' }} · {{ shortCommit(buildInfo.api().commit) }}</span>
          </li>
          <li class="business-switcher">
            <i class="ri-building-4-line"></i>
            <select aria-label="Empresa activa" [value]="auth.session()?.tenantId" (change)="changeBusiness($event)">
              @for (business of auth.session()?.businesses ?? []; track business.businessCode) {
                <option [value]="business.businessCode">{{ business.businessName }}</option>
              }
            </select>
          </li>
          <li class="user-menu">
            <span class="user-avatar">{{ initials }}</span>
            <span class="user-copy d-none d-md-block"><b>{{ displayName }}</b><small>{{ auth.session()?.user?.email }}</small></span>
            <button type="button" (click)="auth.logout()" aria-label="Cerrar sesión" title="Cerrar sesión"><i class="ri-logout-box-r-line"></i></button>
          </li>
        </ul>
      </div>
    </header>
  `,
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {
  isToggled = false;
  currentDate = new Date();

  constructor(private toggleService: ToggleService, public auth: AuthService, public buildInfo: BuildInfoService) {
    this.toggleService.isToggled$.subscribe(v => this.isToggled = v);
  }

  toggle() { this.toggleService.toggle(); }
  shortCommit(commit: string): string { return commit && commit !== 'local' ? commit.slice(0, 7) : (commit || 'sin revisión'); }

  get displayName(): string {
    const user = this.auth.session()?.user;
    return user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.userCode : 'Usuario';
  }
  get initials(): string {
    const user = this.auth.session()?.user;
    return `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase() || 'U';
  }
  get activeRole(): string {
    const role = this.auth.session()?.roles?.[0] ?? 'Acceso fiscal';
    return role.replace('_Carabo', '').replace('_PLATFORM', '').toLowerCase();
  }
  changeBusiness(event: Event): void {
    const code = (event.target as HTMLSelectElement).value;
    if (this.auth.selectBusiness(code)) window.location.reload();
  }
}
