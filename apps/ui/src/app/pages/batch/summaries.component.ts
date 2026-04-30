import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';

@Component({
  selector: 'app-summaries',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatButtonModule, MatTableModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Resúmenes Diarios</h1>
      <p class="gray-color">Resúmenes diarios de boletas enviados a SUNAT</p>
    </div>

    <mat-card>
      <mat-card-header class="d-flex align-items-center justify-content-space-between" style="padding:20px 20px 0;">
        <h3 style="margin:0; font-size:16px;">Historial de resúmenes</h3>
        <button mat-raised-button color="primary">
          <mat-icon>add</mat-icon> Generar resumen
        </button>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        <table mat-table [dataSource]="summaries" class="w-100">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let s">{{ s.date }}</td>
          </ng-container>
          <ng-container matColumnDef="business">
            <th mat-header-cell *matHeaderCellDef>Empresa</th>
            <td mat-cell *matCellDef="let s">{{ s.business }}</td>
          </ng-container>
          <ng-container matColumnDef="count">
            <th mat-header-cell *matHeaderCellDef>Boletas</th>
            <td mat-cell *matCellDef="let s">{{ s.count }}</td>
          </ng-container>
          <ng-container matColumnDef="ticket">
            <th mat-header-cell *matHeaderCellDef>Ticket SUNAT</th>
            <td mat-cell *matCellDef="let s">{{ s.ticket || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let s">
              <span class="badge" [attr.data-status]="s.status">{{ s.status }}</span>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        @if (summaries.length === 0) {
          <div style="text-align:center; padding:40px;">
            <mat-icon style="font-size:48px; width:48px; height:48px; color:#ccc;">calendar_today</mat-icon>
            <p class="gray-color">No hay resúmenes diarios generados.</p>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="SENT"] { background: #fff3e0; color: #e65100; }
  `]
})
export class SummariesComponent {
  displayedColumns = ['date', 'business', 'count', 'ticket', 'status'];
  summaries: any[] = [];
}
