import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, DestroyRef, ChangeDetectionStrategy } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { finalize } from 'rxjs';
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
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './dispatch-guides.component.scss',
})
export class DispatchGuidesComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  columns = ['number', 'receiver', 'route', 'status', 'actions'];
  guides: DispatchGuideSummary[] = [];
  loading = true;
  busyId = '';
  constructor(
    private api: CaraboApiService,
    private snack: MatSnackBar,
  ) {}
  ngOnInit() {
    this.api
      .listDispatchGuides()
      .pipe(finalize(() => (this.loading = false)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (v) => (this.guides = v),
        error: () =>
          this.snack.open('No se pudieron cargar las guías', 'Cerrar', { duration: 4000 }),
      });
  }
  refresh(g: DispatchGuideSummary) {
    this.busyId = g.id;
    this.api
      .checkDispatchGuideStatus(g.id)
      .pipe(finalize(() => (this.busyId = '')))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (v) => {
          Object.assign(g, v);
          this.snack.open(v.sunatResponse || 'Estado actualizado', 'Cerrar', { duration: 4000 });
        },
        error: () => this.snack.open('No se pudo consultar SUNAT', 'Cerrar', { duration: 4000 }),
      });
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
