import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import {
  Component,
  OnInit,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { EMPTY, catchError, exhaustMap, finalize, timer } from 'rxjs';
import { DispatchGuidesApi as CaraboApiService } from '../data-access/dispatch-guides-api.service';
import type { DispatchGuideSummary } from '../domain/dispatch-guides.models';

@Component({
  selector: 'app-dispatch-guides',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './dispatch-guides.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dispatch-guides.component.scss',
})
export class DispatchGuidesComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  columns = ['number', 'receiver', 'route', 'status', 'actions'];
  readonly guides = signal<DispatchGuideSummary[]>([]);
  readonly loading = signal(true);
  readonly busyId = signal('');
  readonly checkedAt = signal<Date | null>(null);
  constructor(
    private api: CaraboApiService,
    private snack: MatSnackBar,
  ) {}
  ngOnInit() {
    timer(0, 15000)
      .pipe(
        exhaustMap(() =>
          this.api.listDispatchGuides().pipe(
            catchError(() => {
              this.snack.open(
                'No se pudieron actualizar las guías; el estado fiscal no está confirmado',
                'Cerrar',
                { duration: 4000 },
              );
              return EMPTY;
            }),
            finalize(() => this.loading.set(false)),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((guides) => {
        this.guides.set(guides);
        this.checkedAt.set(new Date());
      });
  }
  refresh(g: DispatchGuideSummary) {
    this.busyId.set(g.id);
    this.api
      .checkDispatchGuideStatus(g.id)
      .pipe(finalize(() => this.busyId.set('')))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (v) => {
          this.guides.update((guides) => guides.map((guide) => (guide.id === v.id ? v : guide)));
          this.snack.open(v.sunatResponse || 'Estado actualizado', 'Cerrar', { duration: 4000 });
        },
        error: () => this.snack.open('No se pudo consultar SUNAT', 'Cerrar', { duration: 4000 }),
      });
  }
  retry(g: DispatchGuideSummary) {
    if (!g.canRetry || this.busyId()) return;
    this.busyId.set(g.id);
    this.api
      .retryDispatchGuide(g.id)
      .pipe(
        finalize(() => this.busyId.set('')),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (guide) => {
          this.guides.update((rows) => rows.map((row) => (row.id === guide.id ? guide : row)));
          this.snack.open(
            guide.retryAction === 'CHECK_STATUS'
              ? 'Consulta del ticket programada; la guía no se vuelve a enviar'
              : 'Reintento programado para esta misma guía',
            'Cerrar',
            { duration: 5000 },
          );
        },
        error: (error) =>
          this.snack.open(
            error?.error?.message ||
              'No se pudo programar el reintento. Actualiza la lista antes de solicitarlo otra vez',
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }
  statusLabel(status: string): string {
    return (
      (
        {
          RETRY_PENDING: 'Reintento pendiente',
          BLOCKED_CONFIGURATION: 'Envío bloqueado',
          PENDING: 'Pendiente',
          VALIDATED: 'Validada',
          SIGNED: 'Envío sin confirmar',
          SENT: 'Enviada; pendiente de CDR',
          ACCEPTED: 'Aceptada',
          OBSERVED: 'Aceptada con observaciones',
          REJECTED: 'Rechazada',
          ERROR: 'Requiere revisión',
        } as Record<string, string>
      )[status] || status
    );
  }
  download(g: DispatchGuideSummary, type: 'xml' | 'cdr' | 'pdf') {
    this.api
      .downloadDispatchGuide(g.id, type)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `${g.fullNumber}.${type === 'pdf' ? 'pdf' : 'xml'}`;
          a.click();
          URL.revokeObjectURL(url);
        },
        error: () =>
          this.snack.open(`No hay ${type.toUpperCase()} disponible`, 'Cerrar', { duration: 3500 }),
      });
  }
}
