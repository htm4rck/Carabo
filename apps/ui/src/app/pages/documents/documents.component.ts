import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaraboApiService, DocumentSummary, QueryParams } from '../../services/carabo-api.service';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatPaginatorModule, MatTooltipModule, MatSnackBarModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Documentos Electrónicos</h1>
      <p class="gray-color">Lista de documentos emitidos con filtros</p>
    </div>

    <mat-card style="margin-bottom:16px;">
      <mat-card-content style="padding:16px;">
        <div class="filters">
          <mat-form-field appearance="outline">
            <mat-label>Tipo</mat-label>
            <mat-select [(ngModel)]="filters.docType" (selectionChange)="search()">
              <mat-option value="">Todos</mat-option>
              <mat-option value="01">Factura</mat-option>
              <mat-option value="03">Boleta</mat-option>
              <mat-option value="07">Nota de Crédito</mat-option>
              <mat-option value="08">Nota de Débito</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Estado</mat-label>
            <mat-select [(ngModel)]="filters.status" (selectionChange)="search()">
              <mat-option value="">Todos</mat-option>
              <mat-option value="PENDING">Pendiente</mat-option>
              <mat-option value="VALIDATED">Validado</mat-option>
              <mat-option value="ACCEPTED">Aceptado</mat-option>
              <mat-option value="REJECTED">Rechazado</mat-option>
              <mat-option value="ERROR">Error</mat-option>
              <mat-option value="VOIDED">Anulado</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Buscar</mat-label>
            <input matInput [(ngModel)]="filters.search" (keyup.enter)="search()" placeholder="Serie, correlativo, RUC...">
            <mat-icon matSuffix style="cursor:pointer" (click)="search()">search</mat-icon>
          </mat-form-field>
        </div>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-content style="padding:20px;">
        <table mat-table [dataSource]="documents" class="w-100">
          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>Tipo</th>
            <td mat-cell *matCellDef="let d">{{ d.docTypeName }}</td>
          </ng-container>
          <ng-container matColumnDef="fullNumber">
            <th mat-header-cell *matHeaderCellDef>Número</th>
            <td mat-cell *matCellDef="let d" class="fw-semibold">{{ d.fullNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="receiver">
            <th mat-header-cell *matHeaderCellDef>Cliente</th>
            <td mat-cell *matCellDef="let d">{{ d.receiverName }}<br><small class="gray-color">{{ d.receiverDoc }}</small></td>
          </ng-container>
          <ng-container matColumnDef="total">
            <th mat-header-cell *matHeaderCellDef>Total</th>
            <td mat-cell *matCellDef="let d">{{ d.currency }} {{ d.total | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="issueDate">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let d">{{ d.issueDate }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let d">
              <span class="badge" [attr.data-status]="d.status">{{ d.status }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Acciones</th>
            <td mat-cell *matCellDef="let d">
              <button mat-icon-button matTooltip="Ver XML" (click)="openXml(d)"><mat-icon>code</mat-icon></button>
              <button mat-icon-button matTooltip="Descargar PDF" (click)="downloadPdf(d)"><mat-icon>picture_as_pdf</mat-icon></button>
              <button mat-icon-button matTooltip="Descargar CDR" (click)="openCdr(d)"><mat-icon>download</mat-icon></button>
              <button mat-icon-button matTooltip="Reintentar" (click)="retry(d)" [disabled]="d.status === 'ACCEPTED'"><mat-icon>refresh</mat-icon></button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        @if (documents.length === 0 && !loading) {
          <div style="text-align:center; padding:40px;">
            <mat-icon style="font-size:48px; width:48px; height:48px; color:#ccc;">description</mat-icon>
            <p class="gray-color">No hay documentos que coincidan con los filtros.</p>
          </div>
        }

        <mat-paginator
          [length]="totalDocs"
          [pageSize]="pageSize"
          [pageSizeOptions]="[25, 50, 100]"
          (page)="onPage($event)"
          showFirstLastButtons>
        </mat-paginator>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .filters { display: flex; gap: 12px; flex-wrap: wrap; }
    .filters mat-form-field { flex: 1; min-width: 160px; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 11px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="PENDING"], .badge[data-status="VALIDATED"] { background: #fff3e0; color: #e65100; }
    .badge[data-status="ERROR"] { background: #fce4ec; color: #b71c1c; }
    .badge[data-status="VOIDED"] { background: #f3e5f5; color: #6a1b9a; }
  `]
})
export class DocumentsComponent implements OnInit {
  displayedColumns = ['type', 'fullNumber', 'receiver', 'total', 'issueDate', 'status', 'actions'];
  documents: DocumentSummary[] = [];
  totalDocs = 0;
  pageSize = 50;
  loading = false;

  filters: QueryParams = { docType: '', status: '', search: '', offset: 0, limit: 50 };

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar) {}

  ngOnInit() { this.search(); }

  search() {
    this.filters.offset = 0;
    this.loadDocuments();
  }

  onPage(event: PageEvent) {
    this.filters.offset = event.pageIndex * event.pageSize;
    this.filters.limit = event.pageSize;
    this.pageSize = event.pageSize;
    this.loadDocuments();
  }

  private loadDocuments() {
    this.loading = true;
    this.api.queryDocuments(this.filters).subscribe({
      next: (res) => {
        this.documents = res.items;
        this.totalDocs = res.total;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  openXml(doc: DocumentSummary) {
    window.open(this.api.getDocumentXmlUrl(doc.id), '_blank');
  }

  openCdr(doc: DocumentSummary) {
    window.open(this.api.getDocumentCdrUrl(doc.id), '_blank');
  }

  downloadPdf(doc: DocumentSummary) {
    window.open(this.api.getDocumentPdfUrl(doc.id), '_blank');
  }

  retry(doc: DocumentSummary) {
    this.api.retryDocument(doc.id).subscribe({
      next: (updated) => {
        const idx = this.documents.findIndex(d => d.id === doc.id);
        if (idx >= 0) this.documents[idx] = updated;
        this.snackBar.open('Reintento enviado', 'OK', { duration: 3000 });
      },
      error: () => this.snackBar.open('Error al reintentar', 'OK', { duration: 3000 })
    });
  }
}
