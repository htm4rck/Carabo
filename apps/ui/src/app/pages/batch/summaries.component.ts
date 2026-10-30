import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { BatchResult, CaraboApiService } from '../../services/carabo-api.service';
import { AuthService } from '../../auth/auth.service';

interface SummaryView extends BatchResult {
  businessCode: string;
}

@Component({
  selector: 'app-summaries',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatSnackBarModule, MatTooltipModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Resúmenes Diarios</h1>
      <p class="gray-color">Envía a SUNAT las boletas aceptadas de la fecha de emisión seleccionada.</p>
    </div>

    <mat-card style="margin-bottom:16px;">
      <mat-card-content style="padding:20px;">
        <form class="summary-form" #summaryForm="ngForm" (ngSubmit)="generate()">
          <mat-form-field appearance="outline">
            <mat-label>Fecha de emisión</mat-label>
            <input matInput type="date" name="referenceDate" [(ngModel)]="referenceDate" required [disabled]="generating" aria-describedby="summary-date-help">
          </mat-form-field>
          <button type="submit" mat-raised-button color="primary" [disabled]="summaryForm.invalid || generating">
            <mat-icon>{{ generating ? 'hourglass_top' : 'add' }}</mat-icon> {{ generating ? 'Generando…' : 'Generar resumen' }}
          </button>
        </form>
        <p id="summary-date-help" class="date-help">Hoy se calcula en hora de Lima (America/Lima). Puedes elegir hoy o una fecha anterior. El resumen RC incluye boletas; para consultar facturas, usa Reportes Tributarios.</p>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header style="padding:20px 20px 0;">
        <div><h3 style="margin:0; font-size:16px;">Historial de resúmenes</h3><small>Operaciones guardadas para la empresa activa.</small></div>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        @if (lastResult) {
          <section class="operation-result" [attr.data-status]="lastResult.status" role="status" aria-live="polite">
            <mat-icon>{{ resultIcon(lastResult.status) }}</mat-icon>
            <div>
              <strong>{{ statusLabel(lastResult.status) }}</strong>
              <p>{{ lastResult.message }}</p>
              <small>
                Fecha fiscal: {{ lastResult.referenceDate || 'No aplica' }} ·
                {{ lastResult.documentCount }} comprobante(s) ·
                Generado {{ formatGeneratedAt(lastResult.generatedAt, lastResult.timeZone) }}
              </small>
            </div>
          </section>
        }
        @if (operationError) {
          <section class="operation-result" data-status="ERROR" role="alert">
            <mat-icon>error_outline</mat-icon>
            <div><strong>No se pudo completar la operación</strong><p>{{ operationError }}</p></div>
          </section>
        }
        <div class="table-scroll"><table mat-table [dataSource]="summaries" class="w-100" aria-label="Historial de resúmenes diarios">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let s">{{ s.referenceDate || '—' }}</td>
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
              @if (s.id && (s.status === 'ACCEPTED' || s.status === 'OBSERVED')) {
                <button type="button" mat-icon-button matTooltip="Descargar CDR" aria-label="Descargar CDR" (click)="downloadCdr(s)"><mat-icon>verified</mat-icon></button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table></div>

        @if (summaries.length === 0) {
          <div class="empty-state">
            <mat-icon>calendar_today</mat-icon><h2>Aún no generaste resúmenes</h2>
            <p>Selecciona la fecha de emisión y usa “Generar resumen” para enviar las boletas aceptadas de la empresa activa.</p>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .summary-form { display:flex; align-items:flex-start; flex-wrap:wrap; gap:16px; }
    .summary-form button { margin-top:8px; }
    .date-help { margin:0; color:var(--mutedColor); font-size:13px; line-height:1.5; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="OBSERVED"] { background: #fff8dd; color: #7a5a00; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="SENT"] { background: #fff3e0; color: #e65100; }
    .badge[data-status="EMPTY"] { background: #edf3ed; color: #173317; }
    .badge[data-status="ERROR"] { background: #ffebee; color: #a51d2d; }
    mat-card-header small { display:block; margin-top:4px; color:var(--mutedColor); }
    .operation-result { display:flex; gap:12px; align-items:flex-start; margin:0 0 18px; padding:14px 16px; border:1px solid #cbd8cb; border-left:4px solid #768a76; border-radius:8px; background:#f7faf7; color:#173317; }
    .operation-result[data-status="SENT"], .operation-result[data-status="ACCEPTED"] { border-left-color:#2e7d32; background:#f1f8f2; }
    .operation-result[data-status="ERROR"], .operation-result[data-status="REJECTED"] { border-left-color:#b3261e; background:#fff7f6; color:#68120d; }
    .operation-result mat-icon { flex:0 0 auto; margin-top:1px; }
    .operation-result p { margin:2px 0 5px; }
    .operation-result small { color:inherit; opacity:.78; }
    .table-scroll { overflow-x:auto; }
    table { min-width:620px; }
    .empty-state { min-height:210px; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; color:var(--mutedColor); }
    .empty-state mat-icon { width:48px;height:48px;font-size:48px;color:var(--accentColor); }
    .empty-state h2 { margin:12px 0 4px;color:var(--forestColor);font-size:18px; }
    .empty-state p { margin:0; }
    @media(max-width:640px){.summary-form{flex-direction:column;gap:0}.summary-form mat-form-field,.summary-form button{width:100%}.summary-form button{margin:0 0 16px}}
  `]
})
export class SummariesComponent implements OnInit {
  referenceDate = this.todayInLima();
  displayedColumns = ['date', 'business', 'ticket', 'status', 'actions'];
  summaries: SummaryView[] = [];
  generating = false;
  checkingTicket: string | null = null;
  lastResult: SummaryView | null = null;
  operationError = '';

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar, private auth: AuthService, private cdr: ChangeDetectorRef) {}

  private todayInLima(): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(new Date());
    const value = Object.fromEntries(parts.map(part => [part.type, part.value]));
    return `${value['year']}-${value['month']}-${value['day']}`;
  }

  ngOnInit() {
    this.api.listBatchOperations('RC').pipe(
      finalize(() => this.cdr.markForCheck())
    ).subscribe({
      next: operations => this.summaries = operations.map(item => ({
        ...item, businessCode: this.auth.session()?.tenantId ?? ''
      })),
      error: () => this.operationError = 'No se pudo cargar el historial de resúmenes.'
    });
  }

  generate() {
    const bc = this.auth.session()?.tenantId ?? '';
    if (!bc || this.generating) {
      if (!bc) this.operationError = 'Selecciona una empresa antes de generar el resumen.';
      return;
    }
    if (!this.referenceDate) {
      this.operationError = 'Selecciona la fecha de emisión de las boletas.';
      return;
    }
    this.generating = true;
    this.lastResult = null;
    this.operationError = '';
    this.api.generateSummary(bc, this.referenceDate).pipe(
      finalize(() => {
        this.generating = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: (res) => {
        const result = { ...res, businessCode: bc };
        this.lastResult = result;
        this.summaries = [result, ...this.summaries];
        this.snackBar.open(res.message, 'Cerrar', { duration: res.status === 'EMPTY' ? 5000 : 3000 });
      },
      error: (error) => {
        this.operationError = error?.error?.message || error?.error?.details || 'No se pudo generar el resumen. Intenta nuevamente.';
        this.snackBar.open(this.operationError, 'Cerrar', { duration: 5000 });
      }
    });
  }

  checkTicket(s: SummaryView) {
    this.checkingTicket = s.ticket;
    if (!s.ticket) return;
    this.operationError = '';
    this.api.checkTicket(s.businessCode, s.ticket).pipe(
      finalize(() => {
        this.checkingTicket = null;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: (res) => {
        s.status = res.status || (res.success ? 'ACCEPTED' : 'PENDING');
        s.message = res.message;
        s.generatedAt = res.generatedAt;
        s.timeZone = res.timeZone;
        this.lastResult = s;
        this.snackBar.open(res.message || `Ticket: ${res.status || 'consultado'}`, 'Cerrar', { duration: 3000 });
      },
      error: () => {
        this.operationError = 'No se pudo consultar el ticket. Intenta nuevamente.';
        this.snackBar.open(this.operationError, 'Cerrar', { duration: 4000 });
      }
    });
  }

  downloadCdr(s: SummaryView) {
    if (!s.id) return;
    this.api.downloadBatchCdr(s.id).subscribe({
      next: blob => this.saveBlob(blob, `CDR-${s.identifier || 'resumen'}.xml`),
      error: () => this.snackBar.open('El CDR aún no está disponible', 'Cerrar', { duration: 4000 })
    });
  }

  private saveBlob(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
    anchor.href = url; anchor.download = name; anchor.click(); URL.revokeObjectURL(url);
  }

  statusLabel(status: string): string { return ({ EMPTY: 'Sin boletas', SENT: 'Enviado', ACCEPTED: 'Aceptado', OBSERVED: 'Observado', REJECTED: 'Rechazado', PENDING: 'Pendiente', ERROR: 'Error' } as Record<string,string>)[status] ?? status; }

  resultIcon(status: string): string {
    if (status === 'EMPTY') return 'info_outline';
    if (status === 'ERROR' || status === 'REJECTED') return 'error_outline';
    if (status === 'ACCEPTED') return 'check_circle_outline';
    return 'schedule_send';
  }

  formatGeneratedAt(value: string, timeZone = 'America/Lima'): string {
    if (!value) return `en ${timeZone}`;
    const instant = new Date(value);
    if (Number.isNaN(instant.getTime())) return `${value} (${timeZone})`;
    return `${new Intl.DateTimeFormat('es-PE', {
      dateStyle: 'medium', timeStyle: 'medium', timeZone
    }).format(instant)} (${timeZone})`;
  }
}
