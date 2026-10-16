import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaraboApiService, SalesReport } from '../../services/carabo-api.service';
import { AuthService } from '../../auth/auth.service';
import { finalize, take, timeout } from 'rxjs';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule, MatSnackBarModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Reportes Tributarios</h1>
      <p class="gray-color">Registro de ventas y resumen por periodo</p>
    </div>

    <mat-card style="margin-bottom:16px;">
      <mat-card-content style="padding:16px;">
        <form class="filters" #reportForm="ngForm" (ngSubmit)="generate()">
          <mat-form-field appearance="outline">
            <mat-label>Desde</mat-label>
            <input matInput type="date" name="from" [(ngModel)]="from" required>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Hasta</mat-label>
            <input matInput type="date" name="to" [(ngModel)]="to" [min]="from" required>
          </mat-form-field>
          <button type="submit" mat-raised-button color="primary" [disabled]="reportForm.invalid || loading">
            <mat-icon>{{ loading ? 'hourglass_top' : 'assessment' }}</mat-icon> {{ loading ? 'Generando…' : 'Generar reporte' }}
          </button>
          <button type="button" mat-stroked-button [disabled]="reportForm.invalid || loading" (click)="downloadExcel()">
            <mat-icon>download</mat-icon> Descargar Excel
          </button>
        </form>
      </mat-card-content>
    </mat-card>

    @if (errorMessage) {
      <section class="feedback error-feedback" role="alert">
        <mat-icon>error_outline</mat-icon><div><h2>No pudimos generar el reporte</h2><p>{{ errorMessage }}</p></div>
        <button type="button" mat-stroked-button (click)="generate()">Reintentar</button>
      </section>
    } @else if (!report && !loading) {
      <section class="feedback initial-state">
        <mat-icon>query_stats</mat-icon><div><h2>Consulta un periodo tributario</h2><p>Selecciona las fechas para ver ventas, base imponible e IGV.</p></div>
      </section>
    }

    @if (report && !loading) {
      @if (report.totalDocuments === 0) {
        <section class="feedback empty-feedback" role="status" aria-live="polite">
          <mat-icon>event_busy</mat-icon>
          <div>
            <h2>No hay documentos en este periodo</h2>
            <p>No encontramos ventas entre el {{ from }} y el {{ to }} para la empresa activa.</p>
          </div>
        </section>
      }
      <div class="stats-grid">
        <mat-card>
          <mat-card-content>
            <div class="stat"><span class="stat-value">{{ report.totalDocuments }}</span><span class="gray-color">Documentos</span></div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="stat"><span class="stat-value">{{ report.totalTaxBase | number:'1.2-2' }}</span><span class="gray-color">Base Imponible</span></div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="stat"><span class="stat-value">{{ report.totalIgv | number:'1.2-2' }}</span><span class="gray-color">IGV Total</span></div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="stat"><span class="stat-value">{{ report.totalAmount | number:'1.2-2' }}</span><span class="gray-color">Monto Total</span></div>
          </mat-card-content>
        </mat-card>
      </div>

      <mat-card>
        <mat-card-content style="padding:20px;">
          <h3 style="margin:0 0 12px;">Desglose por Tipo de Documento</h3>
          <div class="table-scroll">
          <table mat-table [dataSource]="report.byType" class="w-100" aria-label="Desglose del reporte por tipo de documento">
            <ng-container matColumnDef="docType">
              <th mat-header-cell *matHeaderCellDef>Tipo</th>
              <td mat-cell *matCellDef="let r">{{ r.docType }}</td>
            </ng-container>
            <ng-container matColumnDef="count">
              <th mat-header-cell *matHeaderCellDef>Cantidad</th>
              <td mat-cell *matCellDef="let r">{{ r.count }}</td>
            </ng-container>
            <ng-container matColumnDef="taxBase">
              <th mat-header-cell *matHeaderCellDef>Base Imponible</th>
              <td mat-cell *matCellDef="let r">{{ r.taxBase | number:'1.2-2' }}</td>
            </ng-container>
            <ng-container matColumnDef="igvAmount">
              <th mat-header-cell *matHeaderCellDef>IGV</th>
              <td mat-cell *matCellDef="let r">{{ r.igvAmount | number:'1.2-2' }}</td>
            </ng-container>
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let r">{{ r.total | number:'1.2-2' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="reportColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: reportColumns;"></tr>
          </table>
          </div>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .filters { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .filters mat-form-field { min-width: 160px; }
    .filters button { min-height: 48px; margin-bottom: 20px; }
    .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
    .stat { display: flex; flex-direction: column; align-items: center; padding: 8px; }
    .stat-value { font-size: 22px; font-weight: 800; color:var(--forestColor); font-variant-numeric:tabular-nums; }
    .table-scroll { overflow-x:auto; }
    table { min-width:650px; }
    .feedback { min-height:160px; margin-bottom:16px; padding:26px; display:flex; align-items:center; justify-content:center; gap:18px; border:1px solid var(--borderColor); border-radius:9px; background:#fff; }
    .feedback>mat-icon { width:42px; height:42px; font-size:42px; color:var(--accentColor); }
    .feedback h2 { margin:0 0 4px; color:var(--forestColor); font-size:18px; }
    .feedback p { margin:0; color:var(--mutedColor); }
    .error-feedback { justify-content:flex-start; border-color:#e4c2be; background:#fff8f7; }
    .error-feedback>mat-icon { color:#a14d45; }
    .empty-feedback { justify-content:flex-start; border-color:#cbd8cb; background:#f7faf7; }
    .error-feedback button { margin-left:auto; }
    @media(max-width:900px){.stats-grid{grid-template-columns:repeat(2,1fr)}}
    @media(max-width:560px){.filters{display:grid;grid-template-columns:1fr}.filters mat-form-field{min-width:0}.filters button{width:100%;margin-bottom:0}.stats-grid{grid-template-columns:1fr}.feedback{align-items:flex-start;flex-wrap:wrap}.error-feedback button{width:100%;margin-left:0}}
  `]
})
export class ReportsComponent {
  from = '';
  to = '';
  report: SalesReport | null = null;
  loading = false;
  errorMessage = '';
  reportColumns = ['docType', 'count', 'taxBase', 'igvAmount', 'total'];

  constructor(private api: CaraboApiService, private auth: AuthService, private snackBar: MatSnackBar) {
    const now = new Date();
    this.from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    this.to = now.toISOString().split('T')[0];
  }

  generate() {
    if (!this.from || !this.to || this.from > this.to || this.loading) {
      this.errorMessage = 'La fecha final debe ser igual o posterior a la fecha inicial.';
      return;
    }
    const bc = this.auth.session()?.tenantId ?? '';
    if (!bc) { this.errorMessage = 'Selecciona una empresa antes de generar el reporte.'; return; }
    this.loading = true;
    this.report = null;
    this.errorMessage = '';
    this.api.salesReport(bc, this.from, this.to).pipe(
      take(1),
      timeout({ first: 15000 }),
      finalize(() => this.loading = false)
    ).subscribe({
      next: (r) => {
        this.loading = false;
        this.report = r;
        const message = r.totalDocuments === 0
          ? 'No hay documentos en el periodo seleccionado.'
          : `Reporte generado: ${r.totalDocuments} documento(s).`;
        this.snackBar.open(message, 'Cerrar', { duration: 4500 });
      },
      error: () => {
        this.loading = false;
        this.report = null;
        this.errorMessage = 'No fue posible consultar el periodo. Inténtalo nuevamente.';
        this.snackBar.open(this.errorMessage, 'Cerrar', { duration: 5000 });
      }
    });
  }

  downloadExcel() {
    if (!this.from || !this.to || this.from > this.to) {
      this.errorMessage = 'Selecciona un periodo válido antes de descargar.';
      return;
    }
    this.api.downloadSalesExcel(this.from, this.to).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `registro-ventas-${this.from}-${this.to}.xlsx`;
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => this.errorMessage = 'No fue posible descargar el Excel del periodo.'
    });
  }
}
