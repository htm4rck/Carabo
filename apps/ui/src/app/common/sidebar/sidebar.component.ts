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
    <div class="sidebar-area bg-white box-shadow" [ngClass]="{'active': isToggled}">
      <div class="logo">
        <a routerLink="/" class="d-flex align-items-center">
          <span class="carabo-mark" aria-hidden="true"><i></i><i></i><i></i></span>
          <span class="brand-copy"><b>CARABO</b><small>Facturación electrónica</small></span>
        </a>
      </div>
      <div class="burger-menu" (click)="toggle()" [ngClass]="{'active': isToggled}">
        <span class="top-bar"></span>
        <span class="middle-bar"></span>
        <span class="bottom-bar"></span>
      </div>
      <ng-scrollbar visibility="hover" style="height: 100vh;">
        <div class="sidebar-inner">
          <div class="sidebar-menu">
            <mat-accordion>
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
    </div>
  `,
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  isToggled = false;

  constructor(private toggleService: ToggleService, public auth: AuthService) {
    this.toggleService.isToggled$.subscribe(v => this.isToggled = v);
  }

  toggle() { this.toggleService.toggle(); }
}
