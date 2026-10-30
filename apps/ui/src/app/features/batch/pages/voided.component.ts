import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnInit, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { BatchApi as CaraboApiService } from '../data-access/batch-api.service';
import type { BatchResult } from '../domain/batch.models';
import { AuthService } from '../../../core/session/auth.service';

@Component({
  selector: 'app-voided',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
  ],
  templateUrl: './voided.component.html',
  styleUrl: './voided.component.scss',
})
export class VoidedComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  displayedColumns = ['date', 'kind', 'documents', 'reason', 'ticket', 'status', 'actions'];
  voided: BatchResult[] = [];
  documentIdsInput = '';
  reason = '';
  sending = false;
  checkingTicket: string | null = null;

  constructor(
    private api: CaraboApiService,
    private snackBar: MatSnackBar,
    private auth: AuthService,
  ) {}

  ngOnInit() {
    this.api
      .listBatchOperations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (operations) =>
          (this.voided = operations.filter(
            (operation) => operation.voidCommunication || operation.kind === 'RA',
          )),
        error: () =>
          this.snackBar.open('No se pudo cargar el historial de bajas', 'Cerrar', {
            duration: 4000,
          }),
      });
  }

  sendVoid() {
    const bc = this.auth.session()?.tenantId ?? '';
    const ids = this.documentIdsInput
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (ids.length === 0 || !this.reason) {
      this.snackBar.open('Ingresa los IDs y un motivo', 'Cerrar', { duration: 3000 });
      return;
    }
    if (!bc || this.sending) return;
    this.sending = true;
    this.api
      .sendVoided(bc, ids, this.reason)
      .pipe(finalize(() => (this.sending = false)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.voided.unshift(res);
          this.documentIdsInput = '';
          this.reason = '';
          this.snackBar.open(res.message, 'Cerrar', { duration: 4000 });
        },
        error: () =>
          this.snackBar.open('No se pudo comunicar la baja', 'Cerrar', { duration: 4000 }),
      });
  }

  checkTicket(operation: BatchResult) {
    const bc = this.auth.session()?.tenantId ?? '';
    if (!bc || !operation.ticket) return;
    this.checkingTicket = operation.ticket;
    this.api
      .checkTicket(bc, operation.ticket)
      .pipe(finalize(() => (this.checkingTicket = null)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          Object.assign(operation, result);
          this.snackBar.open(result.message, 'Cerrar', { duration: 4000 });
        },
        error: () =>
          this.snackBar.open('No se pudo consultar el ticket', 'Cerrar', { duration: 4000 }),
      });
  }

  downloadCdr(operation: BatchResult) {
    if (!operation.id) return;
    this.api
      .downloadBatchCdr(operation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `CDR-${operation.identifier || 'baja'}.xml`;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: () =>
          this.snackBar.open('El CDR aún no está disponible', 'Cerrar', { duration: 4000 }),
      });
  }

  statusLabel(status: string): string {
    return (
      (
        {
          EMPTY: 'Sin documentos',
          SENT: 'Enviado',
          PENDING: 'Pendiente',
          ACCEPTED: 'Aceptado',
          OBSERVED: 'Observado',
          REJECTED: 'Rechazado',
          ERROR: 'Error',
        } as Record<string, string>
      )[status] ?? status
    );
  }
}
