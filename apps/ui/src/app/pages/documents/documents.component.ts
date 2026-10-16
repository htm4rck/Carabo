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
import { finalize, take, timeout } from 'rxjs';

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
              <mat-option value="OBSERVED">Observado</mat-option>
              <mat-option value="REJECTED">Rechazado</mat-option>
              <mat-option value="ERROR">Error</mat-option>
              <mat-option value="VOIDED">Anulado</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Buscar</mat-label>
            <input matInput [(ngModel)]="filters.search" (keyup.enter)="search()" placeholder="Serie, correlativo o RUC">
            <button type="button" mat-icon-button matSuffix aria-label="Buscar documentos" (click)="search()"><mat-icon>search</mat-icon></button>
          </mat-form-field>
        </div>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-content style="padding:20px;">
        @if (loading) {
          <div class="state-panel" role="status" aria-live="polite"><mat-icon>hourglass_top</mat-icon><p>Cargando documentos…</p></div>
        } @else if (loadError) {
          <div class="state-panel state-error" role="alert">
            <mat-icon>cloud_off</mat-icon><h2>No pudimos cargar los documentos</h2>
            <p>Verifica la conexión e inténtalo nuevamente.</p>
            <button type="button" mat-stroked-button (click)="loadDocuments()"><mat-icon>refresh</mat-icon> Reintentar</button>
          </div>
        } @else {
        <div class="table-scroll">
        <table mat-table [dataSource]="documents" class="w-100" aria-label="Documentos electrónicos emitidos">
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
            <th mat-header-cell *matHeaderCellDef>Fecha fiscal / generación</th>
            <td mat-cell *matCellDef="let d">
              {{ d.issueDate }}
              <br><small class="gray-color">{{ formatGeneratedAt(d.createdAt, d.timeZone) }}</small>
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let d">
              <span class="badge" [attr.data-status]="d.status">{{ statusLabel(d.status) }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Acciones</th>
            <td mat-cell *matCellDef="let d">
              <button mat-icon-button matTooltip="Ver XML" [attr.aria-label]="'Ver XML de ' + d.fullNumber" (click)="openXml(d)"><mat-icon>code</mat-icon></button>
              <button mat-icon-button matTooltip="Descargar PDF" [attr.aria-label]="'Descargar PDF de ' + d.fullNumber" (click)="downloadPdf(d)"><mat-icon>picture_as_pdf</mat-icon></button>
              <button mat-icon-button matTooltip="Descargar CDR" [attr.aria-label]="'Descargar CDR de ' + d.fullNumber" (click)="openCdr(d)"><mat-icon>download</mat-icon></button>
              <button mat-icon-button matTooltip="Reintentar" [attr.aria-label]="'Reintentar envío de ' + d.fullNumber" (click)="retry(d)" [disabled]="d.status === 'ACCEPTED' || retryingId === d.id"><mat-icon>{{ retryingId === d.id ? 'hourglass_top' : 'refresh' }}</mat-icon></button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
        </div>

        @if (documents.length === 0) {
          <div class="state-panel">
            <mat-icon>description</mat-icon>
            <h2>{{ hasActiveFilters ? 'No encontramos coincidencias' : 'Aún no hay documentos' }}</h2>
            <p>{{ hasActiveFilters ? 'Prueba con otros filtros o limpia la búsqueda.' : 'Los comprobantes emitidos aparecerán aquí.' }}</p>
            @if (hasActiveFilters) { <button type="button" mat-stroked-button (click)="resetFilters()">Limpiar filtros</button> }
          </div>
        }

        @if (totalDocs > 0) {
        <mat-paginator
          [length]="totalDocs"
          [pageSize]="pageSize"
          [pageSizeOptions]="[25, 50, 100]"
          (page)="onPage($event)"
          showFirstLastButtons>
        </mat-paginator>
        }
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .filters { display: flex; gap: 12px; flex-wrap: wrap; }
    .filters mat-form-field { flex: 1; min-width: 160px; }
    .table-scroll { overflow-x: auto; }
    table { min-width: 820px; }
    .state-panel { min-height: 220px; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; padding:32px; color:var(--mutedColor); }
    .state-panel>mat-icon { width:48px; height:48px; font-size:48px; color:var(--accentColor); }
    .state-panel h2 { margin:12px 0 4px; color:var(--forestColor); font-size:18px; }
    .state-panel p { margin:0 0 16px; }
    .state-error>mat-icon { color:#a14d45; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 11px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="OBSERVED"] { background: #fff8dd; color: #7a5a00; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="PENDING"], .badge[data-status="VALIDATED"] { background: #fff3e0; color: #e65100; }
    .badge[data-status="ERROR"] { background: #fce4ec; color: #b71c1c; }
    .badge[data-status="VOIDED"] { background: #f3e5f5; color: #6a1b9a; }
    @media(max-width:767px) { .filters { display:grid; grid-template-columns:1fr; } .filters mat-form-field { min-width:0; } mat-card-content { padding:14px!important; } }
  `]
})
export class DocumentsComponent implements OnInit {
  displayedColumns = ['type', 'fullNumber', 'receiver', 'total', 'issueDate', 'status', 'actions'];
  documents: DocumentSummary[] = [];
  totalDocs = 0;
  pageSize = 50;
  loading = false;
  loadError = false;
  retryingId: string | null = null;

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

  get hasActiveFilters(): boolean { return Boolean(this.filters.docType || this.filters.status || this.filters.search?.trim()); }

  resetFilters() {
    this.filters = { docType: '', status: '', search: '', offset: 0, limit: this.pageSize };
    this.loadDocuments();
  }

  loadDocuments() {
    this.loading = true;
    this.loadError = false;
    this.api.queryDocuments(this.filters).pipe(
      take(1),
      timeout({ first: 15000 }),
      finalize(() => this.loading = false)
    ).subscribe({
      next: (res) => {
        this.loading = false;
        this.documents = res.items ?? [];
        this.totalDocs = res.total ?? 0;
        if (this.documents.length === 0) {
          this.snackBar.open(
            this.hasActiveFilters ? 'No se encontraron documentos con esos filtros.' : 'Aún no hay documentos emitidos.',
            'Cerrar', { duration: 4000 }
          );
        }
      },
      error: () => { this.loading = false; this.loadError = true; }
    });
  }

  openXml(doc: DocumentSummary) {
    this.api.downloadDocumentXml(doc.id).subscribe({
      next: blob => this.openBlob(blob, `${doc.fullNumber}.xml`),
      error: () => this.snackBar.open('No se pudo descargar el XML', 'Cerrar', { duration: 4000 })
    });
  }

  openCdr(doc: DocumentSummary) {
    this.api.downloadDocumentCdr(doc.id).subscribe({
      next: blob => this.openBlob(blob, `CDR-${doc.fullNumber}.xml`),
      error: () => this.snackBar.open('El CDR aún no está disponible', 'Cerrar', { duration: 4000 })
    });
  }

  downloadPdf(doc: DocumentSummary) {
    this.api.downloadDocumentPdf(doc.id).subscribe({
      next: blob => this.openBlob(blob, `${doc.fullNumber}.pdf`),
      error: () => this.snackBar.open('No se pudo generar el PDF', 'Cerrar', { duration: 4000 })
    });
  }

  retry(doc: DocumentSummary) {
    this.retryingId = doc.id;
    this.api.retryDocument(doc.id).subscribe({
      next: (updated) => {
        const idx = this.documents.findIndex(d => d.id === doc.id);
        if (idx >= 0) this.documents[idx] = updated;
        this.retryingId = null;
        this.snackBar.open('Reintento enviado', 'Cerrar', { duration: 3000 });
      },
      error: () => { this.retryingId = null; this.snackBar.open('No se pudo reintentar el envío', 'Cerrar', { duration: 4000 }); }
    });
  }

  statusLabel(status: string): string {
    return ({ PENDING: 'Pendiente', VALIDATED: 'Validado', ACCEPTED: 'Aceptado', OBSERVED: 'Observado', REJECTED: 'Rechazado', ERROR: 'Error', VOIDED: 'Anulado' } as Record<string, string>)[status] ?? status;
  }

  private openBlob(blob: Blob, fileName: string) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  formatGeneratedAt(value: string, timeZone = 'America/Lima'): string {
    if (!value) return `Hora local: ${timeZone}`;
    const instant = new Date(value);
    if (Number.isNaN(instant.getTime())) return `${value} (${timeZone})`;
    return `${new Intl.DateTimeFormat('es-PE', {
      dateStyle: 'short', timeStyle: 'medium', timeZone
    }).format(instant)} (${timeZone})`;
  }
}
