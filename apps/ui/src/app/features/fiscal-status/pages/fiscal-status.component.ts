import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, DestroyRef } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/session/auth.service';
import { FiscalStatusApi as CaraboApiService } from '../data-access/fiscal-status-api.service';
import type { FiscalStatus } from '../domain/fiscal-status.models';

@Component({
  selector: 'app-fiscal-status',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './fiscal-status.component.html',
  styleUrl: './fiscal-status.component.scss',
})
export class FiscalStatusComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  status: FiscalStatus | null = null;
  loading = false;
  error = '';
  readonly routeSteps = [
    { label: 'Empresa', codes: ['tenant-active', 'issuer'] },
    { label: 'Credenciales', codes: ['api-key', 'sol-user', 'sol-password', 'ose-token'] },
    {
      label: 'Certificado',
      codes: [
        'certificate-metadata',
        'certificate-file',
        'certificate-password',
        'certificate-validity',
      ],
    },
    { label: 'SUNAT / OSE', codes: ['send-mode', 'sunat-endpoint', 'ose-endpoint'] },
  ];
  constructor(
    private api: CaraboApiService,
    public auth: AuthService,
  ) {}
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    if (this.loading) return;
    this.loading = true;
    this.error = '';
    this.api
      .getFiscalStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => {
          this.status = status;
          this.loading = false;
        },
        error: () => {
          this.error = 'Verifica tu empresa activa o inténtalo nuevamente.';
          this.loading = false;
        },
      });
  }
  checkReady(codes: string[]): boolean {
    const applicable =
      this.status?.checks.filter((check) => codes.includes(check.code) && check.required) ?? [];
    return applicable.length > 0 && applicable.every((check) => check.ready);
  }
  get readyChecks(): number {
    return this.status?.checks.filter((check) => check.ready).length ?? 0;
  }
  get certificateLabel(): string {
    return (
      {
        VALID: 'Vigente',
        EXPIRING: 'Próximo a vencer',
        CRITICAL: 'Renovación urgente',
        EXPIRED: 'Vencido',
        MISSING: 'No configurado',
      } as Record<string, string>
    )[this.status?.certificate.state ?? 'MISSING'];
  }
  get certificateIcon(): string {
    return this.status?.certificate.state === 'VALID' ? 'verified_user' : 'warning';
  }
}
