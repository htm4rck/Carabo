import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnInit, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ConfigApi as CaraboApiService } from '../data-access/config-api.service';
import type { FiscalReadiness, TenantConfig } from '../domain/config.models';

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTableModule,
    MatChipsModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './config.component.html',
  styleUrl: './config.component.scss',
})
export class ConfigComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  displayedColumns = [
    'businessCode',
    'businessName',
    'sendMode',
    'environment',
    'certificate',
    'status',
    'readiness',
    'actions',
  ];
  empresas: TenantConfig[] = [];
  readiness: Record<string, FiscalReadiness> = {};
  readinessLoading: Record<string, boolean> = {};
  readinessTenant: TenantConfig | null = null;
  editingTenant: TenantConfig | null = null;
  certificateTenant: TenantConfig | null = null;
  draft: Partial<TenantConfig> | null = null;
  selectedCertificate: File | null = null;
  passwordReference = '';
  saving = false;
  uploading = false;
  loading = false;
  loadError = false;

  constructor(
    private api: CaraboApiService,
    private snackBar: MatSnackBar,
  ) {}

  ngOnInit() {
    this.loadTenants();
  }

  loadTenants() {
    this.loading = true;
    this.loadError = false;
    this.api
      .listTenants()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tenants) => {
          this.empresas = tenants;
          this.loading = false;
          tenants.forEach((tenant) => this.loadReadiness(tenant));
        },
        error: () => {
          this.loading = false;
          this.loadError = true;
        },
      });
  }

  editTenant(tenant: TenantConfig) {
    this.closeReadiness();
    this.certificateTenant = null;
    this.editingTenant = tenant;
    this.draft = { ...tenant };
  }

  prepareCertificate(tenant: TenantConfig) {
    this.closeReadiness();
    this.editingTenant = null;
    this.draft = null;
    this.certificateTenant = tenant;
    this.selectedCertificate = null;
    this.passwordReference = `secret://${tenant.businessCode.toLowerCase()}-certificate-password`;
  }

  closePanels() {
    if (this.saving || this.uploading) return;
    this.editingTenant = null;
    this.certificateTenant = null;
    this.draft = null;
    this.selectedCertificate = null;
    this.passwordReference = '';
  }

  get selectedReadiness(): FiscalReadiness | null {
    return this.readinessTenant
      ? (this.readiness[this.readinessTenant.businessCode] ?? null)
      : null;
  }

  loadReadiness(tenant: TenantConfig) {
    if (this.readinessLoading[tenant.businessCode]) return;
    this.readinessLoading[tenant.businessCode] = true;
    this.api
      .getFiscalReadiness(tenant.businessCode)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (state) => {
          this.readiness[tenant.businessCode] = state;
          this.readinessLoading[tenant.businessCode] = false;
        },
        error: () => {
          this.readinessLoading[tenant.businessCode] = false;
        },
      });
  }

  showReadiness(tenant: TenantConfig) {
    this.closePanels();
    this.readinessTenant = tenant;
  }

  closeReadiness() {
    this.readinessTenant = null;
  }

  editFromReadiness() {
    if (!this.readinessTenant) return;
    const tenant = this.readinessTenant;
    this.closeReadiness();
    this.editTenant(tenant);
  }

  readinessLabel(state: FiscalReadiness): string {
    return state.ready
      ? 'Preflight fiscal listo; ver detalle'
      : `${state.blockers} requisitos fiscales pendientes; ver detalle`;
  }

  saveTenant() {
    if (!this.editingTenant || !this.draft || this.saving) return;
    this.saving = true;
    const update = { ...this.draft };
    if (!update.solUser?.trim()) delete update.solUser;
    if (!update.solPassword?.trim()) delete update.solPassword;
    this.api
      .updateTenant(this.editingTenant.id, update)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tenant) => {
          this.replaceTenant(tenant);
          this.saving = false;
          this.closePanels();
          this.snackBar.open('Configuración guardada', 'Cerrar', { duration: 3500 });
        },
        error: (error) => {
          this.saving = false;
          this.snackBar.open(
            this.errorMessage(error, 'No se pudo guardar la configuración'),
            'Cerrar',
            { duration: 5000 },
          );
        },
      });
  }

  selectCertificate(event: Event) {
    const input = event.target as HTMLInputElement;
    this.selectedCertificate = input.files?.item(0) ?? null;
    if (this.selectedCertificate && this.selectedCertificate.size > 5 * 1024 * 1024) {
      this.snackBar.open('El certificado supera el límite de 5 MB', 'Cerrar', { duration: 5000 });
      this.selectedCertificate = null;
      input.value = '';
    }
  }

  canUploadCertificate(): boolean {
    return (
      !!this.selectedCertificate &&
      !!this.certificateTenant &&
      this.passwordReference.startsWith('secret://')
    );
  }

  uploadCertificate() {
    if (
      !this.canUploadCertificate() ||
      !this.selectedCertificate ||
      !this.certificateTenant ||
      this.uploading
    )
      return;
    this.uploading = true;
    this.api
      .uploadTenantCertificate(
        this.certificateTenant.id,
        this.selectedCertificate,
        this.passwordReference.trim(),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tenant) => {
          this.replaceTenant(tenant);
          this.uploading = false;
          this.closePanels();
          this.snackBar.open(`Certificado vigente hasta ${tenant.certificateValidTo}`, 'Cerrar', {
            duration: 5000,
          });
        },
        error: (error) => {
          this.uploading = false;
          this.snackBar.open(
            this.errorMessage(error, 'No se pudo validar el certificado'),
            'Cerrar',
            { duration: 6000 },
          );
        },
      });
  }

  isBusy(tenantId: string): boolean {
    return (
      (this.saving && this.editingTenant?.id === tenantId) ||
      (this.uploading && this.certificateTenant?.id === tenantId)
    );
  }

  formatBytes(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${Math.ceil(bytes / 1024)} KB`
      : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  private replaceTenant(tenant: TenantConfig) {
    this.empresas = this.empresas.map((current) => (current.id === tenant.id ? tenant : current));
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string')
      return error.error.error;
    return fallback;
  }
}
