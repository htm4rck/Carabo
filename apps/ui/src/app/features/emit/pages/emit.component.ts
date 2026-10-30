import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { Router } from '@angular/router';
import { EmitApi as CaraboApiService } from '../data-access/emit-api.service';

interface EmitLine {
  productCode: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  discount: number;
}

@Component({
  selector: 'app-emit',
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
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  templateUrl: './emit.component.html',
  styleUrl: './emit.component.scss',
})
export class EmitComponent {
  private readonly destroyRef = inject(DestroyRef);

  emitting = false;

  form = {
    docType: '01',
    currency: 'PEN',
    series: '',
    receiver: { docType: '6', doc: '', name: '', address: '' },
    reference: { docType: '01', series: '', number: 0, reason: '' },
    lines: [
      { productCode: '', productName: '', quantity: 1, unitPrice: 0, discount: 0 },
    ] as EmitLine[],
  };

  get subtotal(): number {
    return this.form.lines.reduce((sum, l) => sum + (l.quantity * l.unitPrice - l.discount), 0);
  }
  get igv(): number {
    return this.subtotal * 0.18;
  }
  get total(): number {
    return this.subtotal + this.igv;
  }

  constructor(
    private api: CaraboApiService,
    private snackBar: MatSnackBar,
    private router: Router,
  ) {}

  addLine() {
    this.form.lines.push({
      productCode: '',
      productName: '',
      quantity: 1,
      unitPrice: 0,
      discount: 0,
    });
  }

  removeLine(i: number) {
    this.form.lines.splice(i, 1);
  }

  emit() {
    this.emitting = true;
    const payload: any = {
      docType: this.form.docType,
      currency: this.form.currency,
      series: this.form.series || undefined,
      receiver: this.form.receiver,
      lines: this.form.lines.map((l) => ({
        productCode: l.productCode,
        productName: l.productName,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discount: l.discount || 0,
      })),
    };

    if (this.form.docType === '07' || this.form.docType === '08') {
      payload.reference = this.form.reference;
    }

    this.api
      .emitDocument(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.emitting = false;
          this.snackBar
            .open(`Documento ${res.fullNumber} emitido — ${res.status}`, 'Ver', { duration: 5000 })
            .onAction()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.router.navigate(['/documents']));
        },
        error: (err) => {
          this.emitting = false;
          const msg = err.error?.sunatResponse || err.error?.error || 'Error al emitir';
          this.snackBar.open(msg, 'OK', { duration: 5000 });
        },
      });
  }
}
