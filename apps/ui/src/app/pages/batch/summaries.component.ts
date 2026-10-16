import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CaraboApiService } from '../../services/carabo-api.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-summaries',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatSnackBarModule, MatTooltipModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Resúmenes Diarios</h1>
      <p class="gray-color">Resúmenes diarios de boletas enviados a SUNAT</p>
    </div>

    <mat-card>
      <mat-card-header class="d-flex align-items-center justify-content-space-between" style="padding:20px 20px 0;">
        <div><h3 style="margin:0; font-size:16px;">Actividad de esta sesión</h3><small>Los resúmenes generados mientras esta vista permanezca abierta.</small></div>
        <button type="button" mat-raised-button color="primary" (click)="generate()" [disabled]="generating">
          <mat-icon>{{ generating ? 'hourglass_top' : 'add' }}</mat-icon> {{ generating ? 'Generando…' : 'Generar resumen' }}
        </button>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        <div class="table-scroll"><table mat-table [dataSource]="summaries" class="w-100" aria-label="Resúmenes generados en esta sesión">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let s">{{ s.date }}</td>
          </ng-container>
          <ng-container matColumnDef="business">
            <th mat-header-cell *matHeaderCellDef>Empresa</th>
            <td mat-cell *matCellDef="let s">{{ s.businessCode }}</td>
          </ng-container>
          <ng-container matColumnDef="ticket">
            <th mat-header-cell *matHeaderCellDef>Ticket SUNAT</th>
            <td mat-cell *matCellDef="let s">{{ s.ticket || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let s">
              <span class="badge" [attr.data-status]="s.status">{{ statusLabel(s.status) }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Acciones</th>
            <td mat-cell *matCellDef="let s">
              @if (s.ticket) {
                <button type="button" mat-icon-button matTooltip="Consultar ticket" [attr.aria-label]="'Consultar ticket ' + s.ticket" (click)="checkTicket(s)" [disabled]="checkingTicket === s.ticket">
                  <mat-icon>{{ checkingTicket === s.ticket ? 'hourglass_top' : 'sync' }}</mat-icon>
                </button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table></div>

        @if (summaries.length === 0) {
          <div class="empty-state">
            <mat-icon>calendar_today</mat-icon><h2>Aún no generaste resúmenes</h2>
            <p>Usa “Generar resumen” para enviar las boletas pendientes de la empresa activa.</p>
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
    .badge[data-status="OBSERVED"] { background: #fff8dd; color: #7a5a00; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="SENT"] { background: #fff3e0; color: #e65100; }
    mat-card-header small { display:block; margin-top:4px; color:var(--mutedColor); }
    .table-scroll { overflow-x:auto; }
    table { min-width:620px; }
    .empty-state { min-height:210px; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; color:var(--mutedColor); }
    .empty-state mat-icon { width:48px;height:48px;font-size:48px;color:var(--accentColor); }
    .empty-state h2 { margin:12px 0 4px;color:var(--forestColor);font-size:18px; }
    .empty-state p { margin:0; }
    @media(max-width:640px){mat-card-header{align-items:flex-start!important;flex-direction:column;gap:14px}mat-card-header button{width:100%}}
  `]
})
export class SummariesComponent {
  displayedColumns = ['date', 'business', 'ticket', 'status', 'actions'];
  summaries: any[] = [];
  generating = false;
  checkingTicket: string | null = null;

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar, private auth: AuthService) {}

  generate() {
    const bc = this.auth.session()?.tenantId ?? '';
    if (!bc || this.generating) return;
    this.generating = true;
    this.api.generateSummary(bc).subscribe({
      next: (res) => {
        this.generating = false;
        this.summaries.unshift(res);
        this.snackBar.open('Resumen generado', 'Cerrar', { duration: 3000 });
      },
      error: () => { this.generating = false; this.snackBar.open('No se pudo generar el resumen', 'Cerrar', { duration: 4000 }); }
    });
  }

  checkTicket(s: any) {
    this.checkingTicket = s.ticket;
    this.api.checkTicket(s.businessCode, s.ticket).subscribe({
      next: (res) => {
        this.checkingTicket = null;
        s.status = res.status || (res.success ? 'ACCEPTED' : 'PENDING');
        this.snackBar.open(`Ticket: ${res.status || 'consultado'}`, 'Cerrar', { duration: 3000 });
      },
      error: () => { this.checkingTicket = null; this.snackBar.open('No se pudo consultar el ticket', 'Cerrar', { duration: 4000 }); }
    });
  }

  statusLabel(status: string): string { return ({ SENT: 'Enviado', ACCEPTED: 'Aceptado', OBSERVED: 'Observado', REJECTED: 'Rechazado', PENDING: 'Pendiente', ERROR: 'Error' } as Record<string,string>)[status] ?? status; }
}
