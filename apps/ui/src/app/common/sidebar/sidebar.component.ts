import { Component } from '@angular/core';
import { ToggleService } from '../header/toggle.service';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass } from '@angular/common';
import { NgScrollbarModule } from 'ngx-scrollbar';
import { MatExpansionModule } from '@angular/material/expansion';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgClass, NgScrollbarModule, MatExpansionModule],
  template: `
    <div class="sidebar-area bg-white box-shadow" [ngClass]="{'active': isToggled}">
      <div class="logo bg-white">
        <a routerLink="/" class="d-flex align-items-center">
          <span style="font-size: 32px; margin-right: 8px;">🦉</span>
          <span class="d-flex flex-column" style="line-height: 1.2;">
            Carabo
            <small style="font-size: 10px; font-weight: 400; opacity: 0.6;">Facturación Electrónica</small>
          </span>
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
              <span class="sub-title gray-color">INICIO</span>
              <a routerLink="/" class="sidebar-menu-link" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}">
                <i class="ri-dashboard-line"></i>
                <span class="title">Dashboard</span>
              </a>

              <span class="sub-title gray-color">CONFIGURACIÓN</span>
              <a routerLink="/config" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-settings-3-line"></i>
                <span class="title">Empresas</span>
              </a>

              <span class="sub-title gray-color">DOCUMENTOS</span>
              <a routerLink="/documents" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-file-list-3-line"></i>
                <span class="title">Documentos</span>
              </a>

              <span class="sub-title gray-color">OPERACIONES</span>
              <a routerLink="/batch/summaries" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-calendar-check-line"></i>
                <span class="title">Resúmenes Diarios</span>
              </a>
              <a routerLink="/batch/voided" class="sidebar-menu-link" routerLinkActive="active">
                <i class="ri-close-circle-line"></i>
                <span class="title">Comunicaciones de Baja</span>
              </a>
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

  constructor(private toggleService: ToggleService) {
    this.toggleService.isToggled$.subscribe(v => this.isToggled = v);
  }

  toggle() { this.toggleService.toggle(); }
}
