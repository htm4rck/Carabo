import { Component, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { CaraboApiService, DocumentSummary } from '../../services/carabo-api.service';
import { AuthService } from '../../auth/auth.service';

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
  imports: [MatCardModule, MatIconModule, RouterLink, DecimalPipe],
  template: `
    <div class="breadcrumb-area">
      <h1>Dashboard</h1>
      <p class="gray-color">Métricas de facturación electrónica</p>
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
        <mat-card-content style="padding:16px;">
          @if (recentDocs.length === 0) {
            <p class="gray-color">No hay documentos emitidos aún.</p>
          } @else {
            @for (doc of recentDocs; track doc.id) {
              <div class="recent-row">
                <span class="doc-number">{{ doc.fullNumber }}</span>
                <span class="doc-type">{{ doc.docTypeName }}</span>
                <span class="doc-receiver">{{ doc.receiverName }}</span>
                <span class="badge" [attr.data-status]="doc.status">{{ doc.status }}</span>
                <span class="doc-total">{{ doc.currency }} {{ doc.total | number:'1.2-2' }}</span>
              </div>
            }
          }
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
    .type-code { background: var(--heraBlueColor, #1565c0); color: #fff; padding: 6px 10px; border-radius: 6px; font-weight: 600; font-size: 13px; min-width: 32px; text-align: center; }
    .type-info { display: flex; flex-direction: column; }
    .recent-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
    .doc-number { font-weight: 600; min-width: 140px; }
    .doc-type { font-size: 11px; color: #666; min-width: 100px; }
    .doc-receiver { flex: 1; font-size: 12px; }
    .doc-total { font-weight: 600; min-width: 100px; text-align: right; }
    .badge { padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="PENDING"] { background: #fff3e0; color: #e65100; }
    .badge[data-status="ERROR"] { background: #fce4ec; color: #b71c1c; }
  `]
})
export class DashboardComponent implements OnInit {
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

  recentDocs: DocumentSummary[] = [];

  constructor(private api: CaraboApiService, private auth: AuthService) {}

  ngOnInit() {
    this.loadStats();
  }

  private loadStats() {
    // Load recent documents
    this.api.queryDocuments({ limit: 10, offset: 0 }).subscribe({
      next: (res) => {
        this.recentDocs = res.items;
        this.cards[0].value = res.total;

        // Count by status
        const accepted = res.items.filter(d => d.status === 'ACCEPTED').length;
        const rejected = res.items.filter(d => d.status === 'REJECTED').length;
        const pending = res.items.filter(d => ['PENDING', 'VALIDATED', 'SIGNED', 'SENT'].includes(d.status)).length;
        this.cards[1].value = accepted;
        this.cards[2].value = rejected;
        this.cards[3].value = pending;

        // Count by type
        for (const dt of this.docTypes) {
          dt.count = res.items.filter(d => d.docType === dt.code).length;
        }
      },
      error: () => {}
    });
  }
}
