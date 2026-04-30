import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';

@Component({
  selector: 'app-voided',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatButtonModule, MatTableModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Comunicaciones de Baja</h1>
      <p class="gray-color">Documentos anulados enviados a SUNAT</p>
    </div>

    <mat-card>
      <mat-card-header class="d-flex align-items-center justify-content-space-between" style="padding:20px 20px 0;">
        <h3 style="margin:0; font-size:16px;">Historial de bajas</h3>
        <button mat-raised-button color="primary">
          <mat-icon>add</mat-icon> Nueva baja
        </button>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        <table mat-table [dataSource]="voided" class="w-100">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let v">{{ v.date }}</td>
          </ng-container>
          <ng-container matColumnDef="business">
            <th mat-header-cell *matHeaderCellDef>Empresa</th>
            <td mat-cell *matCellDef="let v">{{ v.business }}</td>
          </ng-container>
          <ng-container matColumnDef="documents">
            <th mat-header-cell *matHeaderCellDef>Documentos</th>
            <td mat-cell *matCellDef="let v">{{ v.documentCount }}</td>
          </ng-container>
          <ng-container matColumnDef="ticket">
            <th mat-header-cell *matHeaderCellDef>Ticket SUNAT</th>
            <td mat-cell *matCellDef="let v">{{ v.ticket || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let v">
              <span class="badge" [attr.data-status]="v.status">{{ v.status }}</span>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        @if (voided.length === 0) {
          <div style="text-align:center; padding:40px;">
            <mat-icon style="font-size:48px; width:48px; height:48px; color:#ccc;">block</mat-icon>
            <p class="gray-color">No hay comunicaciones de baja registradas.</p>
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
export class VoidedComponent {
  displayedColumns = ['date', 'business', 'documents', 'ticket', 'status'];
  voided: any[] = [];
}
