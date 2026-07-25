import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaraboApiService } from '../../services/carabo-api.service';

@Component({
  selector: 'app-voided',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSnackBarModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Comunicaciones de Baja</h1>
      <p class="gray-color">Documentos anulados enviados a SUNAT</p>
    </div>

    <mat-card style="margin-bottom:16px;">
      <mat-card-content style="padding:16px;">
        <h4 style="margin:0 0 8px; font-size:13px; color:#666;">Anular documentos</h4>
        <div class="void-form">
          <mat-form-field appearance="outline" style="flex:2;">
            <mat-label>IDs de documentos (separados por coma)</mat-label>
            <input matInput [(ngModel)]="documentIdsInput" placeholder="uuid1, uuid2...">
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1;">
            <mat-label>Motivo</mat-label>
            <input matInput [(ngModel)]="reason">
          </mat-form-field>
          <button mat-raised-button color="warn" (click)="sendVoid()">
            <mat-icon>block</mat-icon> Anular
          </button>
        </div>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header style="padding:20px 20px 0;">
        <h3 style="margin:0; font-size:16px;">Historial de bajas</h3>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        <table mat-table [dataSource]="voided" class="w-100">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let v">{{ v.date }}</td>
          </ng-container>
          <ng-container matColumnDef="documents">
            <th mat-header-cell *matHeaderCellDef>Documentos</th>
            <td mat-cell *matCellDef="let v">{{ v.documentCount }}</td>
          </ng-container>
          <ng-container matColumnDef="reason">
            <th mat-header-cell *matHeaderCellDef>Motivo</th>
            <td mat-cell *matCellDef="let v">{{ v.reason }}</td>
          </ng-container>
          <ng-container matColumnDef="ticket">
            <th mat-header-cell *matHeaderCellDef>Ticket</th>
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
    .void-form { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    h4 { margin: 0; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="SENT"] { background: #fff3e0; color: #e65100; }
  `]
})
export class VoidedComponent {
  displayedColumns = ['date', 'documents', 'reason', 'ticket', 'status'];
  voided: any[] = [];
  documentIdsInput = '';
  reason = '';

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar) {}

  sendVoid() {
    const bc = localStorage.getItem('carabo_selected_business') || '';
    const ids = this.documentIdsInput.split(',').map(s => s.trim()).filter(Boolean);
    if (ids.length === 0 || !this.reason) {
      this.snackBar.open('Ingrese IDs y motivo', 'OK', { duration: 3000 });
      return;
    }
    this.api.sendVoided(bc, ids, this.reason).subscribe({
      next: (res) => {
        this.voided.unshift({ date: new Date().toISOString().split('T')[0], documentCount: ids.length, reason: this.reason, ticket: res.ticket, status: 'SENT' });
        this.documentIdsInput = '';
        this.reason = '';
        this.snackBar.open('Comunicación de baja enviada', 'OK', { duration: 3000 });
      },
      error: () => this.snackBar.open('Error al enviar baja', 'OK', { duration: 3000 })
    });
  }
}
