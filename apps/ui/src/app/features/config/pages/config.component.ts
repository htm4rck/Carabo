import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Injector,
  afterNextRender,
} from '@angular/core';
import { finalize } from 'rxjs';
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
import type {
  FiscalReadiness,
  TenantConfig,
  CredentialField,
  CredentialValues,
  CredentialStatus,
} from '../domain/config.models';

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
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './config.component.scss',
})
export class ConfigComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly injector = inject(Injector);

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
  certificatePassword = '';
  credentials: CredentialStatus | null = null;
  credentialsLoading = false;
  credentialsSaving = false;
  credentialError = '';
  credentialMessage = '';
  credentialFile: File | null = null;
  private credentialInput: HTMLInputElement | null = null;
  private credentialSequence = 0;
  editingCredentialGroup: string | null = null;
  credentialImportOpen = false;
  credentialValues = this.emptyCredentials();
  readonly credentialFields: Array<{
    key: CredentialField;
    label: string;
    replacementLabel: string;
    secret: boolean;
    max: number;
  }> = [
    {
      key: 'solUser',
      replacementLabel: 'Nuevo usuario SOL',
      label: 'Usuario SOL',
      secret: false,
      max: 100,
    },
    {
      key: 'solPassword',
      replacementLabel: 'Nueva contraseña SOL',
      label: 'Contraseña SOL',
      secret: true,
      max: 4096,
    },
    {
      key: 'greClientId',
      replacementLabel: 'Nuevo ID de cliente SUNAT GRE',
      label: 'ID de cliente SUNAT GRE',
      secret: false,
      max: 200,
    },
    {
      key: 'greClientSecret',
      replacementLabel: 'Nueva clave SUNAT GRE',
      label: 'Clave SUNAT GRE',
      secret: true,
      max: 4096,
    },
    {
      key: 'certificatePassword',
      replacementLabel: 'Nueva contraseña del certificado',
      label: 'Contraseña del certificado',
      secret: true,
      max: 4096,
    },
    {
      key: 'oseApiToken',
      replacementLabel: 'Nuevo token OSE',
      label: 'Token OSE (opcional)',
      secret: true,
      max: 4096,
    },
  ];
  readonly credentialGroups = [
    {
      key: 'sol',
      title: 'Acceso SOL',
      description: 'Usuario y contraseña del emisor en SUNAT.',
      fields: this.credentialFields.filter((field) =>
        ['solUser', 'solPassword'].includes(field.key),
      ),
    },
    {
      key: 'gre',
      title: 'SUNAT GRE',
      description: 'Credenciales de la aplicación para las guías de remisión.',
      fields: this.credentialFields.filter((field) =>
        ['greClientId', 'greClientSecret'].includes(field.key),
      ),
    },
    {
      key: 'certificate',
      title: 'Certificado digital',
      description: 'Contraseña del archivo de firma .p12 o .pfx.',
      fields: this.credentialFields.filter((field) => field.key === 'certificatePassword'),
    },
    {
      key: 'ose',
      title: 'Proveedor OSE',
      description: 'Token opcional. Se usa cuando el canal de envío es OSE.',
      fields: this.credentialFields.filter((field) => field.key === 'oseApiToken'),
    },
  ];
  credentialGroupHasValue(group: { fields: readonly { key: CredentialField }[] }): boolean {
    return group.fields.some((field) => this.credentials?.fields[field.key].configured);
  }
  editCredentialGroup(key: string) {
    if (this.credentialsSaving || this.saving || !this.credentials?.storageReady) return;
    this.clearCredentialInputs();
    this.credentialError = '';
    this.credentialMessage = '';
    this.credentialImportOpen = false;
    this.editingCredentialGroup = key;
    const firstField = this.credentialGroups.find((group) => group.key === key)?.fields[0];
    if (firstField) this.focusCredentialControl('credential-' + firstField.key);
  }
  cancelCredentialEdit() {
    if (this.credentialsSaving) return;
    const group = this.editingCredentialGroup;
    this.editingCredentialGroup = null;
    this.clearCredentialInputs();
    this.credentialError = '';
    if (group) this.focusCredentialControl('credential-action-' + group);
  }
  toggleCredentialImport() {
    if (this.credentialsSaving || this.saving) return;
    this.clearCredentialInputs();
    this.editingCredentialGroup = null;
    this.credentialError = '';
    this.credentialMessage = '';
    this.credentialImportOpen = !this.credentialImportOpen;
    if (this.credentialImportOpen) this.focusCredentialControl('credential-file');
  }
  private focusCredentialControl(id: string) {
    afterNextRender(() => document.getElementById(id)?.focus(), { injector: this.injector });
  }
  private emptyCredentials(): CredentialValues {
    return {
      solUser: '',
      solPassword: '',
      greClientId: '',
      greClientSecret: '',
      certificatePassword: '',
      oseApiToken: '',
    };
  }
  private clearCredentialInputs() {
    this.credentialValues = this.emptyCredentials();
    this.credentialFile = null;
    if (this.credentialInput) this.credentialInput.value = '';
  }
  ngOnDestroy() {
    this.clearCredentialInputs();
    this.editingCredentialGroup = null;
    this.credentialImportOpen = false;
    this.certificatePassword = '';
  }
  private loadCredentials(tenant: TenantConfig) {
    const sequence = ++this.credentialSequence;
    this.credentials = null;
    this.credentialsLoading = true;
    this.api
      .credentialStatus(tenant.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
      .subscribe({
        next: (status) => {
          if (sequence === this.credentialSequence && this.editingTenant?.id === tenant.id) {
            this.credentials = status;
            this.credentialsLoading = false;
          }
        },
        error: () => {
          if (sequence === this.credentialSequence) {
            this.credentialsLoading = false;
            this.credentialError =
              'No pudimos consultar las credenciales. Vuelve a abrir la configuración.';
          }
        },
      });
  }
  canSaveCredentials() {
    return (
      !!this.credentials?.storageReady &&
      !this.credentialsSaving &&
      !this.saving &&
      Object.values(this.credentialValues).some((value) => value.trim().length > 0)
    );
  }
  saveCredentials() {
    if (!this.editingTenant || !this.canSaveCredentials()) return;
    this.credentialsSaving = true;
    this.credentialError = '';
    this.credentialMessage = '';
    const tenant = this.editingTenant;
    this.api
      .saveCredentials(tenant.id, this.credentialValues)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
      .subscribe({
        next: (status) => this.credentialsSaved(tenant, status),
        error: (error) => this.credentialsFailed(error),
      });
  }
  selectCredentialFile(event: Event) {
    this.credentialInput = event.target as HTMLInputElement;
    this.credentialFile = this.credentialInput.files?.item(0) ?? null;
    this.credentialError = '';
    if (this.credentialFile && this.credentialFile.size > 16384) {
      this.credentialError = 'El archivo supera el límite de 16 KB';
      this.credentialFile = null;
      this.credentialInput.value = '';
    }
  }
  importCredentials() {
    if (
      !this.editingTenant ||
      !this.credentialFile ||
      !this.credentials?.storageReady ||
      this.credentialsSaving ||
      this.saving
    )
      return;
    this.credentialsSaving = true;
    this.credentialError = '';
    this.credentialMessage = '';
    const tenant = this.editingTenant;
    this.api
      .importCredentials(tenant.id, this.credentialFile)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
      .subscribe({
        next: (status) => this.credentialsSaved(tenant, status),
        error: (error) => this.credentialsFailed(error),
      });
  }
  downloadCredentialTemplate() {
    if (!this.editingTenant) return;
    this.api
      .credentialTemplate(this.editingTenant.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob),
            anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = 'carabo-credenciales-vacias.txt';
          anchor.click();
          setTimeout(() => URL.revokeObjectURL(url), 0);
        },
        error: () => {
          this.credentialError = 'No pudimos descargar la plantilla vacía';
        },
      });
  }
  private credentialsSaved(tenant: TenantConfig, status: CredentialStatus) {
    this.credentials = status;
    this.credentialsSaving = false;
    this.clearCredentialInputs();
    const group = this.editingCredentialGroup;
    this.editingCredentialGroup = null;
    this.credentialMessage = 'Credenciales guardadas.';
    if (group) this.focusCredentialControl('credential-action-' + group);
    this.loadReadiness(tenant);
  }
  private credentialsFailed(error: unknown) {
    this.credentialsSaving = false;
    this.credentialError = this.errorMessage(error, 'No se pudieron guardar las credenciales');
  }
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
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
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
    if (this.credentialsSaving || this.saving || this.uploading) return;
    this.clearCredentialInputs();
    this.editingCredentialGroup = null;
    this.credentialImportOpen = false;
    this.certificatePassword = '';
    this.credentialError = '';
    this.credentialMessage = '';
    this.closeReadiness();
    this.certificateTenant = null;
    this.editingTenant = tenant;
    this.draft = { ...tenant };
    this.loadCredentials(tenant);
  }

  prepareCertificate(tenant: TenantConfig) {
    if (this.credentialsSaving || this.saving || this.uploading) return;
    this.clearCredentialInputs();
    this.editingCredentialGroup = null;
    this.credentialImportOpen = false;
    this.closeReadiness();
    this.editingTenant = null;
    this.draft = null;
    this.certificateTenant = tenant;
    this.selectedCertificate = null;
    this.certificatePassword = '';
  }

  closePanels() {
    if (this.saving || this.uploading || this.credentialsSaving) return;
    this.clearCredentialInputs();
    this.editingCredentialGroup = null;
    this.credentialImportOpen = false;
    ++this.credentialSequence;
    this.editingTenant = null;
    this.certificateTenant = null;
    this.draft = null;
    this.selectedCertificate = null;
    this.certificatePassword = '';
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
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
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
    if (!this.editingTenant || !this.draft || this.saving || this.credentialsSaving) return;
    this.saving = true;
    const update = { ...this.draft };
    if (!update.solUser?.trim()) delete update.solUser;
    if (!update.solPassword?.trim()) delete update.solPassword;
    if (!update.greClientId?.trim()) delete update.greClientId;
    if (!update.greClientSecret?.trim()) delete update.greClientSecret;
    this.api
      .updateTenant(this.editingTenant.id, update)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
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
    return !!this.selectedCertificate && !!this.certificateTenant;
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
        '',
        this.certificatePassword,
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cdr.markForCheck()),
      )
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
      ((this.saving || this.credentialsSaving) && this.editingTenant?.id === tenantId) ||
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
