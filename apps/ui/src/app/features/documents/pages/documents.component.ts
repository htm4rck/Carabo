import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChangeDetectorRef, Component, OnInit, inject, DestroyRef } from '@angular/core';
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
import { DocumentsApi as CaraboApiService } from '../data-access/documents-api.service';
import type { DocumentSummary, QueryParams } from '../domain/documents.models';
import { finalize, take, timeout } from 'rxjs';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatSnackBarModule,
  ],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss',
})
export class DocumentsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  displayedColumns = ['type', 'fullNumber', 'receiver', 'total', 'issueDate', 'status', 'actions'];
  documents: DocumentSummary[] = [];
  totalDocs = 0;
  pageSize = 50;
  loading = false;
  loadError = false;
  retryingId: string | null = null;

  filters: QueryParams = { docType: '', status: '', search: '', offset: 0, limit: 50 };

  constructor(
    private api: CaraboApiService,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.search();
  }

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

  get hasActiveFilters(): boolean {
    return Boolean(this.filters.docType || this.filters.status || this.filters.search?.trim());
  }

  resetFilters() {
    this.filters = { docType: '', status: '', search: '', offset: 0, limit: this.pageSize };
    this.loadDocuments();
  }

  loadDocuments() {
    this.loading = true;
    this.loadError = false;
    this.api
      .queryDocuments(this.filters)
      .pipe(
        take(1),
        timeout({ first: 15000 }),
        // The application runs without Zone.js. Explicitly trigger rendering when
        // the HTTP observable settles; otherwise the completed data only appears
        // after an unrelated browser event (for example, resizing the viewport).
        finalize(() => this.updateView(() => (this.loading = false))),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.updateView(() => {
            this.documents = res.items ?? [];
            this.totalDocs = res.total ?? 0;
            if (this.documents.length === 0) {
              this.snackBar.open(
                this.hasActiveFilters
                  ? 'No se encontraron documentos con esos filtros.'
                  : 'Aún no hay documentos emitidos.',
                'Cerrar',
                { duration: 4000 },
              );
            }
          });
        },
        error: () => this.updateView(() => (this.loadError = true)),
      });
  }

  openXml(doc: DocumentSummary) {
    this.api
      .downloadDocumentXml(doc.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.openBlob(blob, `${this.documentReference(doc)}.xml`),
        error: () =>
          this.snackBar.open('No se pudo descargar el XML', 'Cerrar', { duration: 4000 }),
      });
  }

  openCdr(doc: DocumentSummary) {
    this.api
      .downloadDocumentCdr(doc.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.openBlob(blob, `CDR-${this.documentReference(doc)}.xml`),
        error: () =>
          this.snackBar.open('El CDR aún no está disponible', 'Cerrar', { duration: 4000 }),
      });
  }

  downloadPdf(doc: DocumentSummary) {
    this.api
      .downloadDocumentPdf(doc.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.openBlob(blob, `${this.documentReference(doc)}.pdf`),
        error: () => this.snackBar.open('No se pudo generar el PDF', 'Cerrar', { duration: 4000 }),
      });
  }

  retry(doc: DocumentSummary) {
    this.retryingId = doc.id;
    this.api
      .retryDocument(doc.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          const idx = this.documents.findIndex((d) => d.id === doc.id);
          if (idx >= 0) this.documents[idx] = updated;
          this.retryingId = null;
          this.snackBar.open('Reintento enviado', 'Cerrar', { duration: 3000 });
        },
        error: () => {
          this.retryingId = null;
          this.snackBar.open('No se pudo reintentar el envío', 'Cerrar', { duration: 4000 });
        },
      });
  }

  statusLabel(status: string): string {
    return (
      (
        {
          PENDING: 'Pendiente',
          VALIDATED: 'Validado',
          BLOCKED_CONFIGURATION: 'Bloqueado por configuración',
          RETRY_PENDING: 'Reintento pendiente',
          ACCEPTED: 'Aceptado',
          OBSERVED: 'Observado',
          REJECTED: 'Rechazado',
          ERROR: 'Error',
          VOIDED: 'Anulado',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  documentReference(doc: DocumentSummary): string {
    return doc.fullNumber ?? doc.externalId ?? 'Sin correlativo fiscal';
  }

  canRetry(doc: DocumentSummary): boolean {
    return doc.status === 'ERROR' || doc.status === 'REJECTED' || doc.status === 'RETRY_PENDING';
  }

  private updateView(update: () => void) {
    update();
    this.cdr.detectChanges();
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
      dateStyle: 'short',
      timeStyle: 'medium',
      timeZone,
    }).format(instant)} (${timeZone})`;
  }
}
