import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnInit, signal, inject, DestroyRef } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DocumentsApi as CaraboApiService } from '../../documents/public-api';
import type { DocumentSummary } from '../../documents/public-api';
import { AuthService } from '../../../core/session/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly loadError = signal(false);
  recentDocs: DocumentSummary[] = [];
  totalDocuments = 0;
  acceptedCount = 0;
  pendingCount = 0;
  attentionCount = 0;
  signedCount = 0;
  activeTypeCount = 0;

  constructor(
    private readonly api: CaraboApiService,
    public readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.loadStats();
  }
  get sampleCount(): number {
    return this.recentDocs.length;
  }
  get greeting(): string {
    const hour = new Date().getHours();
    return hour < 12 ? 'Buenos días' : hour < 19 ? 'Buenas tardes' : 'Buenas noches';
  }
  get roleLabel(): string {
    return (this.auth.session()?.roles?.[0] ?? 'Usuario')
      .replace('_Carabo', '')
      .replace('_PLATFORM', '')
      .toLowerCase();
  }

  loadStats(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.api
      .queryDocuments({ limit: 10, offset: 0 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.recentDocs = response.items;
          this.totalDocuments = response.total;
          this.acceptedCount = response.items.filter((doc) => doc.status === 'ACCEPTED').length;
          this.pendingCount = response.items.filter((doc) =>
            ['PENDING', 'VALIDATED', 'SIGNED', 'SENT'].includes(doc.status),
          ).length;
          this.attentionCount = response.items.filter((doc) =>
            ['REJECTED', 'ERROR'].includes(doc.status),
          ).length;
          this.signedCount = response.items.filter((doc) =>
            ['SIGNED', 'SENT', 'ACCEPTED', 'REJECTED'].includes(doc.status),
          ).length;
          this.activeTypeCount = new Set(response.items.map((doc) => doc.docType)).size;
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.loadError.set(true);
        },
      });
  }

  statusLabel(status: string): string {
    return (
      (
        {
          ACCEPTED: 'Aceptado',
          REJECTED: 'Rechazado',
          ERROR: 'Error',
          PENDING: 'Pendiente',
          VALIDATED: 'Validado',
          SIGNED: 'Firmado',
          SENT: 'Enviado',
        } as Record<string, string>
      )[status] ?? status
    );
  }
}
