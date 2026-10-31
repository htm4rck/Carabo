import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, DestroyRef, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ReportsApi as CaraboApiService } from '../data-access/reports-api.service';
import type { SalesReport } from '../domain/reports.models';
import { AuthService } from '../../../core/session/auth.service';
import { finalize, take, timeout } from 'rxjs';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatSnackBarModule,
  ],
  templateUrl: './reports.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './reports.component.scss',
})
export class ReportsComponent {
  private readonly destroyRef = inject(DestroyRef);

  from = '';
  to = '';
  report: SalesReport | null = null;
  loading = false;
  errorMessage = '';
  reportColumns = ['docType', 'count', 'taxBase', 'igvAmount', 'total'];

  constructor(
    private api: CaraboApiService,
    private auth: AuthService,
    private snackBar: MatSnackBar,
  ) {
    const now = new Date();
    this.from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    this.to = now.toISOString().split('T')[0];
  }

  generate() {
    if (!this.from || !this.to || this.from > this.to || this.loading) {
      this.errorMessage = 'La fecha final debe ser igual o posterior a la fecha inicial.';
      return;
    }
    const bc = this.auth.session()?.tenantId ?? '';
    if (!bc) {
      this.errorMessage = 'Selecciona una empresa antes de generar el reporte.';
      return;
    }
    this.loading = true;
    this.report = null;
    this.errorMessage = '';
    this.api
      .salesReport(bc, this.from, this.to)
      .pipe(
        take(1),
        timeout({ first: 15000 }),
        finalize(() => (this.loading = false)),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          this.loading = false;
          this.report = r;
          const message =
            r.totalDocuments === 0
              ? 'No hay documentos en el periodo seleccionado.'
              : `Reporte generado: ${r.totalDocuments} documento(s).`;
          this.snackBar.open(message, 'Cerrar', { duration: 4500 });
        },
        error: () => {
          this.loading = false;
          this.report = null;
          this.errorMessage = 'No fue posible consultar el periodo. Inténtalo nuevamente.';
          this.snackBar.open(this.errorMessage, 'Cerrar', { duration: 5000 });
        },
      });
  }

  downloadExcel() {
    if (!this.from || !this.to || this.from > this.to) {
      this.errorMessage = 'Selecciona un periodo válido antes de descargar.';
      return;
    }
    this.api
      .downloadSalesExcel(this.from, this.to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `registro-ventas-${this.from}-${this.to}.xlsx`;
          anchor.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        },
        error: () => (this.errorMessage = 'No fue posible descargar el Excel del periodo.'),
      });
  }
}
