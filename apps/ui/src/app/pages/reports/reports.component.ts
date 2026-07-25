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
import { CaraboApiService, SalesReport } from '../../services/carabo-api.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Reportes Tributarios</h1>
      <p class="gray-color">Registro de ventas y resumen por periodo</p>
    </div>

    <mat-card style="margin-bottom:16px;">
      <mat-card-content style="padding:16px;">
        <div class="filters">
          <mat-form-field appearance="outline">
            <mat-label>Desde</mat-label>
            <input matInput type="date" [(ngModel)]="from">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Hasta</mat-label>
            <input matInput type="date" [(ngModel)]="to">
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="generate()">
            <mat-icon>assessment</mat-icon> Generar
          </button>
        </div>
      </mat-card-content>
    </mat-card>

    @if (report) {
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
          <table mat-table [dataSource]="report.byType" class="w-100">
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
    .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
    .stat { display: flex; flex-direction: column; align-items: center; padding: 8px; }
    .stat-value { font-size: 22px; font-weight: 700; }
  `]
})
export class ReportsComponent {
  from = '';
  to = '';
  report: SalesReport | null = null;
  reportColumns = ['docType', 'count', 'taxBase', 'igvAmount', 'total'];

  constructor(private api: CaraboApiService) {
    const now = new Date();
    this.from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    this.to = now.toISOString().split('T')[0];
  }

  generate() {
    // businessCode comes from selected tenant context
    const bc = localStorage.getItem('carabo_selected_business') || '';
    this.api.salesReport(bc, this.from, this.to).subscribe({
      next: (r) => this.report = r,
      error: () => {}
    });
  }
}
