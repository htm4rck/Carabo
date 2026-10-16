import { Component } from '@angular/core';
import { ToggleService } from '../header/toggle.service';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass } from '@angular/common';
import { NgScrollbarModule } from 'ngx-scrollbar';
import { MatExpansionModule } from '@angular/material/expansion';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgClass, NgScrollbarModule, MatExpansionModule],
  template: `
    <nav
      id="carabo-navigation"
      class="sidebar-area bg-white box-shadow"
      [ngClass]="{'collapsed': desktopCollapsed, 'open': mobileOpen}"
      aria-label="Navegación principal"
    >
      <div class="logo">
        <a routerLink="/" class="d-flex align-items-center" aria-label="Carabo, ir al centro fiscal">
          <img class="brand-icon" src="assets/images/icono.png" alt="">
          <span class="brand-copy"><b>CARABO</b><small>Facturación electrónica</small></span>
        </a>
        <button type="button" class="drawer-close" (click)="closeMobile()" aria-label="Cerrar menú principal">
          <i class="ri-close-line" aria-hidden="true"></i>
        </button>
      </div>
      <ng-scrollbar visibility="hover" style="height: 100dvh;">
        <div class="sidebar-inner">
          <div class="sidebar-menu">
            <mat-accordion (click)="closeAfterNavigation($event)">
              <span class="sub-title">OPERACIÓN</span>
              <a routerLink="/" class="sidebar-menu-link" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}">
                <i class="ri-dashboard-line"></i>
                <span class="title">Centro fiscal</span>
              </a>

              <a routerLink="/documents" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-file-list-3-line"></i>
                <span class="title">Documentos</span>
              </a>
              <a routerLink="/emit" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-send-plane-line"></i>
                <span class="title">Emisión Manual</span>
              </a>

              @if (auth.canSupervise()) {
                <span class="sub-title">PROCESOS FISCALES</span>
                <a routerLink="/batch/summaries" class="sidebar-menu-link" routerLinkActive="active">
                  <i class="ri-calendar-check-line"></i><span class="title">Resúmenes diarios</span>
                </a>
                <a routerLink="/batch/voided" class="sidebar-menu-link" routerLinkActive="active">
                  <i class="ri-close-circle-line"></i><span class="title">Comunicaciones de baja</span>
                </a>
              }

              <span class="sub-title">ANÁLISIS</span>
              <a routerLink="/reports" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-bar-chart-2-line"></i>
                <span class="title">Reportes Tributarios</span>
              </a>
              @if (auth.canAdminister()) {
                <span class="sub-title">ADMINISTRACIÓN</span>
                <a routerLink="/config" class="sidebar-menu-link" routerLinkActive="active">
                  <i class="ri-settings-3-line"></i><span class="title">Empresas</span>
                </a>
              }
            </mat-accordion>
          </div>
        </div>
      </ng-scrollbar>
    </nav>
  `,
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  desktopCollapsed = false;
  mobileOpen = false;

  constructor(private toggleService: ToggleService, public auth: AuthService) {
    this.toggleService.desktopCollapsed$.subscribe(value => this.desktopCollapsed = value);
    this.toggleService.mobileOpen$.subscribe(value => this.mobileOpen = value);
  }

  closeMobile(): void {
    this.toggleService.closeMobile();
    queueMicrotask(() => document.querySelector<HTMLButtonElement>('.header-area .burger-menu')?.focus());
  }

  closeAfterNavigation(event: Event): void {
    if ((event.target as HTMLElement).closest('a.sidebar-menu-link')) this.toggleService.closeMobile();
  }
}
