import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

interface StatCard {
  icon: string;
  label: string;
  value: number | string;
  route: string;
  color: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, MatIconModule, RouterLink],
  template: `
    <div class="breadcrumb-area">
      <h1>Dashboard</h1>
      <p class="gray-color">Métricas de facturación electrónica por empresa</p>
    </div>

    <div class="stats-grid">
      @for (card of cards; track card.label) {
        <mat-card class="stat-card" [routerLink]="card.route" style="cursor:pointer;">
          <mat-card-content>
            <div class="stat-row">
              <div class="stat-icon" [style.background]="card.color + '18'" [style.color]="card.color">
                <mat-icon>{{ card.icon }}</mat-icon>
              </div>
              <div class="stat-info">
                <span class="stat-value">{{ card.value }}</span>
                <span class="stat-label gray-color">{{ card.label }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      }
    </div>

    <div class="section">
      <h3>Documentos recientes</h3>
      <mat-card>
        <mat-card-content>
          <p class="gray-color">No hay documentos emitidos aún. Configura una empresa para comenzar.</p>
        </mat-card-content>
      </mat-card>
    </div>

    <div class="section">
      <h3>Desglose por tipo</h3>
      <div class="type-grid">
        @for (doc of docTypes; track doc.code) {
          <mat-card>
            <mat-card-content>
              <div class="type-row">
                <span class="type-code">{{ doc.code }}</span>
                <div class="type-info">
                  <span class="fw-semibold">{{ doc.name }}</span>
                  <span class="gray-color" style="font-size:12px;">{{ doc.count }} emitidos</span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>
        }
      </div>
    </div>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; margin-bottom: 32px; }
    .stat-card { transition: box-shadow .2s; }
    .stat-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.12); }
    .stat-row { display: flex; align-items: center; gap: 16px; }
    .stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; }
    .stat-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .stat-info { display: flex; flex-direction: column; }
    .stat-value { font-size: 24px; font-weight: 600; line-height: 1.2; }
    .stat-label { font-size: 12px; }
    .section { margin-bottom: 24px; }
    .section h3 { margin: 0 0 16px; font-size: 16px; }
    .type-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
    .type-row { display: flex; align-items: center; gap: 12px; }
    .type-code { background: var(--heraBlueColor); color: #fff; padding: 6px 10px; border-radius: 6px; font-weight: 600; font-size: 13px; min-width: 32px; text-align: center; }
    .type-info { display: flex; flex-direction: column; }
  `]
})
export class DashboardComponent {
  cards: StatCard[] = [
    { icon: 'send', label: 'Emitidos', value: 0, route: '/documents', color: '#1976d2' },
    { icon: 'check_circle', label: 'Aceptados', value: 0, route: '/documents', color: '#388e3c' },
    { icon: 'cancel', label: 'Rechazados', value: 0, route: '/documents', color: '#d32f2f' },
    { icon: 'hourglass_empty', label: 'Pendientes', value: 0, route: '/documents', color: '#f57c00' },
    { icon: 'business', label: 'Empresas', value: 0, route: '/config', color: '#7b1fa2' },
  ];

  docTypes = [
    { code: '01', name: 'Factura', count: 0 },
    { code: '03', name: 'Boleta', count: 0 },
    { code: '07', name: 'Nota de Crédito', count: 0 },
    { code: '08', name: 'Nota de Débito', count: 0 },
    { code: '09', name: 'Guía de Remisión', count: 0 },
  ];
}
