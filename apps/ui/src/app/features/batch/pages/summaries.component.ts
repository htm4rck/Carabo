import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ChangeDetectorRef,
  Component,
  OnInit,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
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
import { BatchApi as CaraboApiService } from '../data-access/batch-api.service';
import type { BatchResult } from '../domain/batch.models';
import { AuthService } from '../../../core/session/auth.service';

interface SummaryView extends BatchResult {
  businessCode: string;
}

@Component({
  selector: 'app-summaries',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTableModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './summaries.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './summaries.component.scss',
})
export class SummariesComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  referenceDate = this.todayInLima();
  displayedColumns = ['date', 'business', 'ticket', 'status', 'actions'];
  summaries: SummaryView[] = [];
  generating = false;
  checkingTicket: string | null = null;
  lastResult: SummaryView | null = null;
  operationError = '';

  constructor(
    private api: CaraboApiService,
    private snackBar: MatSnackBar,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  private todayInLima(): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Lima',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());
    const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
    return `${value['year']}-${value['month']}-${value['day']}`;
  }

  ngOnInit() {
    this.api
      .listBatchOperations('RC')
      .pipe(finalize(() => this.cdr.markForCheck()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (operations) =>
          (this.summaries = operations.map((item) => ({
            ...item,
            businessCode: this.auth.session()?.tenantId ?? '',
          }))),
        error: () => (this.operationError = 'No se pudo cargar el historial de resúmenes.'),
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
    this.api
      .generateSummary(bc, this.referenceDate)
      .pipe(
        finalize(() => {
          this.generating = false;
          this.cdr.markForCheck();
        }),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const result = { ...res, businessCode: bc };
          this.lastResult = result;
          this.summaries = [result, ...this.summaries];
          this.snackBar.open(res.message, 'Cerrar', {
            duration: res.status === 'EMPTY' ? 5000 : 3000,
          });
        },
        error: (error) => {
          this.operationError =
            error?.error?.message ||
            error?.error?.details ||
            'No se pudo generar el resumen. Intenta nuevamente.';
          this.snackBar.open(this.operationError, 'Cerrar', { duration: 5000 });
        },
      });
  }

  checkTicket(s: SummaryView) {
    this.checkingTicket = s.ticket;
    if (!s.ticket) return;
    this.operationError = '';
    this.api
      .checkTicket(s.businessCode, s.ticket)
      .pipe(
        finalize(() => {
          this.checkingTicket = null;
          this.cdr.markForCheck();
        }),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          s.status = res.status || (res.success ? 'ACCEPTED' : 'PENDING');
          s.message = res.message;
          s.generatedAt = res.generatedAt;
          s.timeZone = res.timeZone;
          this.lastResult = s;
          this.snackBar.open(res.message || `Ticket: ${res.status || 'consultado'}`, 'Cerrar', {
            duration: 3000,
          });
        },
        error: () => {
          this.operationError = 'No se pudo consultar el ticket. Intenta nuevamente.';
          this.snackBar.open(this.operationError, 'Cerrar', { duration: 4000 });
        },
      });
  }

  downloadCdr(s: SummaryView) {
    if (!s.id) return;
    this.api
      .downloadBatchCdr(s.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.saveBlob(blob, `CDR-${s.identifier || 'resumen'}.xml`),
        error: () =>
          this.snackBar.open('El CDR aún no está disponible', 'Cerrar', { duration: 4000 }),
      });
  }

  private saveBlob(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  statusLabel(status: string): string {
    return (
      (
        {
          EMPTY: 'Sin boletas',
          SENT: 'Enviado',
          ACCEPTED: 'Aceptado',
          OBSERVED: 'Observado',
          REJECTED: 'Rechazado',
          PENDING: 'Pendiente',
          ERROR: 'Error',
        } as Record<string, string>
      )[status] ?? status
    );
  }

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
      dateStyle: 'medium',
      timeStyle: 'medium',
      timeZone,
    }).format(instant)} (${timeZone})`;
  }
}
