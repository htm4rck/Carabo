import { Component } from '@angular/core';
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
import { CaraboApiService } from '../../services/carabo-api.service';

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
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatSnackBarModule, MatDividerModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Emisión Manual</h1>
      <p class="gray-color">Emitir un documento electrónico manualmente</p>
    </div>

    <div class="form-grid">
      <mat-card>
        <mat-card-header><h3 style="margin:0; padding:16px 16px 0;">Datos del Documento</h3></mat-card-header>
        <mat-card-content style="padding:16px;">
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Tipo de Documento</mat-label>
              <mat-select [(ngModel)]="form.docType">
                <mat-option value="01">Factura</mat-option>
                <mat-option value="03">Boleta</mat-option>
                <mat-option value="07">Nota de Crédito</mat-option>
                <mat-option value="08">Nota de Débito</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Moneda</mat-label>
              <mat-select [(ngModel)]="form.currency">
                <mat-option value="PEN">PEN - Soles</mat-option>
                <mat-option value="USD">USD - Dólares</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Serie (opcional)</mat-label>
              <input matInput [(ngModel)]="form.series" placeholder="F001">
            </mat-form-field>
          </div>

          <mat-divider style="margin:12px 0;"></mat-divider>
          <h4>Receptor</h4>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Tipo Doc.</mat-label>
              <mat-select [(ngModel)]="form.receiver.docType">
                <mat-option value="6">RUC</mat-option>
                <mat-option value="1">DNI</mat-option>
                <mat-option value="4">CE</mat-option>
                <mat-option value="0">Sin doc.</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Número</mat-label>
              <input matInput [(ngModel)]="form.receiver.doc">
            </mat-form-field>
            <mat-form-field appearance="outline" style="flex:2;">
              <mat-label>Razón Social / Nombre</mat-label>
              <input matInput [(ngModel)]="form.receiver.name">
            </mat-form-field>
          </div>

          @if (form.docType === '07' || form.docType === '08') {
            <mat-divider style="margin:12px 0;"></mat-divider>
            <h4>Documento de Referencia</h4>
            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label>Tipo</mat-label>
                <mat-select [(ngModel)]="form.reference.docType">
                  <mat-option value="01">Factura</mat-option>
                  <mat-option value="03">Boleta</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Serie</mat-label>
                <input matInput [(ngModel)]="form.reference.series">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Número</mat-label>
                <input matInput type="number" [(ngModel)]="form.reference.number">
              </mat-form-field>
              <mat-form-field appearance="outline" style="flex:2;">
                <mat-label>Motivo</mat-label>
                <input matInput [(ngModel)]="form.reference.reason">
              </mat-form-field>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header><h3 style="margin:0; padding:16px 16px 0;">Líneas de Detalle</h3></mat-card-header>
        <mat-card-content style="padding:16px;">
          @for (line of form.lines; track $index; let i = $index) {
            <div class="line-row">
              <mat-form-field appearance="outline" class="code-field">
                <mat-label>Código</mat-label>
                <input matInput [(ngModel)]="line.productCode">
              </mat-form-field>
              <mat-form-field appearance="outline" class="desc-field">
                <mat-label>Descripción</mat-label>
                <input matInput [(ngModel)]="line.productName">
              </mat-form-field>
              <mat-form-field appearance="outline" class="num-field">
                <mat-label>Cant.</mat-label>
                <input matInput type="number" [(ngModel)]="line.quantity">
              </mat-form-field>
              <mat-form-field appearance="outline" class="num-field">
                <mat-label>P. Unit.</mat-label>
                <input matInput type="number" [(ngModel)]="line.unitPrice">
              </mat-form-field>
              <mat-form-field appearance="outline" class="num-field">
                <mat-label>Dscto.</mat-label>
                <input matInput type="number" [(ngModel)]="line.discount">
              </mat-form-field>
              <button mat-icon-button color="warn" (click)="removeLine(i)" [disabled]="form.lines.length <= 1">
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
          <button mat-stroked-button (click)="addLine()"><mat-icon>add</mat-icon> Agregar línea</button>

          <mat-divider style="margin:16px 0;"></mat-divider>
          <div class="totals-preview">
            <div class="total-row"><span>Subtotal:</span><span>{{ form.currency }} {{ subtotal | number:'1.2-2' }}</span></div>
            <div class="total-row"><span>IGV (18%):</span><span>{{ form.currency }} {{ igv | number:'1.2-2' }}</span></div>
            <div class="total-row total-final"><span>TOTAL:</span><span>{{ form.currency }} {{ total | number:'1.2-2' }}</span></div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>

    <div class="actions-bar">
      <button mat-raised-button color="primary" (click)="emit()" [disabled]="emitting">
        <mat-icon>send</mat-icon> {{ emitting ? 'Emitiendo...' : 'Emitir Documento' }}
      </button>
    </div>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .form-grid { display: flex; flex-direction: column; gap: 16px; }
    .row { display: flex; gap: 12px; flex-wrap: wrap; }
    .row mat-form-field { flex: 1; min-width: 140px; }
    h4 { margin: 0 0 8px; font-size: 13px; color: #666; }
    .line-row { display: flex; gap: 8px; align-items: center; margin-bottom: 4px; }
    .code-field { width: 100px; }
    .desc-field { flex: 2; }
    .num-field { width: 90px; }
    .totals-preview { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
    .total-row { display: flex; gap: 24px; font-size: 13px; }
    .total-final { font-size: 16px; font-weight: 700; border-top: 2px solid #1565c0; padding-top: 4px; }
    .actions-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
  `]
})
export class EmitComponent {
  emitting = false;

  form = {
    docType: '01',
    currency: 'PEN',
    series: '',
    receiver: { docType: '6', doc: '', name: '', address: '' },
    reference: { docType: '01', series: '', number: 0, reason: '' },
    lines: [{ productCode: '', productName: '', quantity: 1, unitPrice: 0, discount: 0 }] as EmitLine[],
  };

  get subtotal(): number {
    return this.form.lines.reduce((sum, l) => sum + (l.quantity * l.unitPrice - l.discount), 0);
  }
  get igv(): number { return this.subtotal * 0.18; }
  get total(): number { return this.subtotal + this.igv; }

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar, private router: Router) {}

  addLine() {
    this.form.lines.push({ productCode: '', productName: '', quantity: 1, unitPrice: 0, discount: 0 });
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
      lines: this.form.lines.map(l => ({
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

    this.api.emitDocument(payload).subscribe({
      next: (res) => {
        this.emitting = false;
        this.snackBar.open(`Documento ${res.fullNumber} emitido — ${res.status}`, 'Ver', { duration: 5000 })
          .onAction().subscribe(() => this.router.navigate(['/documents']));
      },
      error: (err) => {
        this.emitting = false;
        const msg = err.error?.sunatResponse || err.error?.error || 'Error al emitir';
        this.snackBar.open(msg, 'OK', { duration: 5000 });
      }
    });
  }
}
