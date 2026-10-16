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
import { AuthService } from '../../auth/auth.service';

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
        <form class="void-form" #voidForm="ngForm" (ngSubmit)="sendVoid()">
          <mat-form-field appearance="outline" style="flex:2;">
            <mat-label>IDs de documentos (separados por coma)</mat-label>
            <input matInput name="documentIds" [(ngModel)]="documentIdsInput" placeholder="uuid1, uuid2" required>
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1;">
            <mat-label>Motivo</mat-label>
            <input matInput name="reason" [(ngModel)]="reason" minlength="3" maxlength="500" required>
          </mat-form-field>
          <button type="submit" mat-raised-button color="warn" [disabled]="voidForm.invalid || sending">
            <mat-icon>{{ sending ? 'hourglass_top' : 'block' }}</mat-icon> {{ sending ? 'Enviando…' : 'Comunicar baja' }}
          </button>
        </form>
        <p class="risk-note"><mat-icon>info</mat-icon> Esta acción comunica la anulación a SUNAT; verifica los identificadores antes de continuar.</p>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header style="padding:20px 20px 0;">
        <div><h3 style="margin:0; font-size:16px;">Actividad de esta sesión</h3><small>Las comunicaciones enviadas mientras esta vista permanezca abierta.</small></div>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        <div class="table-scroll"><table mat-table [dataSource]="voided" class="w-100" aria-label="Comunicaciones de baja enviadas en esta sesión">
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
              <span class="badge" [attr.data-status]="v.status">{{ statusLabel(v.status) }}</span>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table></div>

        @if (voided.length === 0) {
          <div class="empty-state">
            <mat-icon>block</mat-icon><h2>No hay bajas en esta sesión</h2>
            <p>Las comunicaciones que envíes aparecerán aquí para su seguimiento inmediato.</p>
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
    .void-form button { min-height:48px; margin-bottom:20px; }
    .risk-note { display:flex;align-items:center;gap:7px;margin:0;color:var(--mutedColor);font-size:12px; }
    .risk-note mat-icon { width:17px;height:17px;font-size:17px; }
    h4 { margin: 0; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="SENT"] { background: #fff3e0; color: #e65100; }
    mat-card-header small { display:block;margin-top:4px;color:var(--mutedColor); }
    .table-scroll { overflow-x:auto; }
    table { min-width:650px; }
    .empty-state { min-height:210px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:var(--mutedColor); }
    .empty-state mat-icon { width:48px;height:48px;font-size:48px;color:var(--accentColor); }
    .empty-state h2 { margin:12px 0 4px;color:var(--forestColor);font-size:18px; }
    .empty-state p { margin:0; }
    @media(max-width:700px){.void-form{display:grid;grid-template-columns:1fr}.void-form mat-form-field{min-width:0}.void-form button{width:100%;margin-bottom:0}}
  `]
})
export class VoidedComponent {
  displayedColumns = ['date', 'documents', 'reason', 'ticket', 'status'];
  voided: any[] = [];
  documentIdsInput = '';
  reason = '';
  sending = false;

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar, private auth: AuthService) {}

  sendVoid() {
    const bc = this.auth.session()?.tenantId ?? '';
    const ids = this.documentIdsInput.split(',').map(s => s.trim()).filter(Boolean);
    if (ids.length === 0 || !this.reason) {
      this.snackBar.open('Ingresa los IDs y un motivo', 'Cerrar', { duration: 3000 });
      return;
    }
    if (!bc || this.sending) return;
    this.sending = true;
    this.api.sendVoided(bc, ids, this.reason).subscribe({
      next: (res) => {
        this.sending = false;
        this.voided.unshift({ date: new Date().toISOString().split('T')[0], documentCount: ids.length, reason: this.reason, ticket: res.ticket, status: 'SENT' });
        this.documentIdsInput = '';
        this.reason = '';
        this.snackBar.open('Comunicación de baja enviada', 'Cerrar', { duration: 3000 });
      },
      error: () => { this.sending = false; this.snackBar.open('No se pudo comunicar la baja', 'Cerrar', { duration: 4000 }); }
    });
  }

  statusLabel(status: string): string { return ({ SENT: 'Enviado', ACCEPTED: 'Aceptado', REJECTED: 'Rechazado' } as Record<string,string>)[status] ?? status; }
}
