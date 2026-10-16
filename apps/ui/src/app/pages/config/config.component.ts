import { Component, OnInit } from '@angular/core';
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
import { CaraboApiService, TenantConfig } from '../../services/carabo-api.service';

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatChipsModule, MatSnackBarModule, MatTooltipModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Configuración de Empresas</h1>
      <p class="gray-color">Gestión de facturación electrónica por empresa</p>
    </div>

    <mat-card>
      <mat-card-header class="d-flex align-items-center justify-content-space-between" style="padding:20px 20px 0;">
        <h3 style="margin:0; font-size:16px;">Empresas configuradas</h3>
        <span class="auth-context"><mat-icon>verified_user</mat-icon> Empresas habilitadas por Tordo Auth</span>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        @if (loading) {
          <div class="state-panel" role="status" aria-live="polite"><mat-icon>hourglass_top</mat-icon><p>Cargando configuración…</p></div>
        } @else if (loadError) {
          <div class="state-panel state-error" role="alert"><mat-icon>cloud_off</mat-icon><h2>No pudimos cargar las empresas</h2><p>La configuración permanece intacta.</p><button type="button" mat-stroked-button (click)="loadTenants()"><mat-icon>refresh</mat-icon> Reintentar</button></div>
        } @else {
        <div class="table-scroll">
        <table mat-table [dataSource]="empresas" class="w-100" aria-label="Configuración de empresas">
          <ng-container matColumnDef="businessCode">
            <th mat-header-cell *matHeaderCellDef>Código</th>
            <td mat-cell *matCellDef="let e">{{ e.businessCode }}</td>
          </ng-container>
          <ng-container matColumnDef="businessName">
            <th mat-header-cell *matHeaderCellDef>Empresa</th>
            <td mat-cell *matCellDef="let e">{{ e.businessName || e.issuerName }}</td>
          </ng-container>
          <ng-container matColumnDef="sendMode">
            <th mat-header-cell *matHeaderCellDef>Modo</th>
            <td mat-cell *matCellDef="let e">
              <mat-chip-set>
                <mat-chip [highlighted]="e.sendMode === 'OSE'">{{ e.sendMode }}</mat-chip>
              </mat-chip-set>
            </td>
          </ng-container>
          <ng-container matColumnDef="environment">
            <th mat-header-cell *matHeaderCellDef>Ambiente</th>
            <td mat-cell *matCellDef="let e">
              <span class="env-badge" [attr.data-env]="e.environment">{{ e.environment }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="certificate">
            <th mat-header-cell *matHeaderCellDef>Certificado</th>
            <td mat-cell *matCellDef="let e">
              <span class="certificate-status" [class.missing]="!e.certificateFile"><mat-icon>
                {{ e.certificateFile ? 'verified' : 'warning' }}
              </mat-icon>{{ e.certificateFile ? 'Configurado' : 'Pendiente' }}</span>
              @if (e.certificateValidTo) {
                <small class="gray-color">{{ e.certificateValidTo }}</small>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let e">
              <span class="badge" [class.active]="e.active">{{ e.active ? 'Activo' : 'Inactivo' }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Acciones</th>
            <td mat-cell *matCellDef="let e">
              <div class="row-actions">
                <button mat-icon-button matTooltip="Configurar empresa" [attr.aria-label]="'Configurar ' + (e.businessName || e.businessCode)"
                  [disabled]="isBusy(e.id)" (click)="editTenant(e)"><mat-icon>settings</mat-icon></button>
                <button mat-icon-button matTooltip="Subir certificado" [attr.aria-label]="'Subir certificado de ' + (e.businessName || e.businessCode)"
                  [disabled]="isBusy(e.id)" (click)="prepareCertificate(e)"><mat-icon>upload_file</mat-icon></button>
              </div>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
        </div>

        @if (empresas.length === 0) {
          <div class="state-panel">
            <mat-icon>business</mat-icon><h2>No hay empresas disponibles</h2>
            <p>Solicita en Tordo Auth el acceso de una empresa a Carabo.</p>
          </div>
        }
        }

        @if (editingTenant && draft) {
          <section class="action-panel" aria-labelledby="tenant-editor-title">
            <div class="panel-heading">
              <div>
                <h2 id="tenant-editor-title">Configurar {{ editingTenant.businessName || editingTenant.businessCode }}</h2>
                <p>Actualiza la identidad fiscal, el canal de envío y las series documentales.</p>
              </div>
              <button type="button" mat-icon-button aria-label="Cerrar configuración" (click)="closePanels()"><mat-icon>close</mat-icon></button>
            </div>
            <form #tenantForm="ngForm" (ngSubmit)="saveTenant()">
              <div class="form-grid">
                <label>Razón social
                  <input name="businessName" [(ngModel)]="draft.businessName" maxlength="300" required>
                </label>
                <label>RUC del emisor
                  <input name="issuerRuc" [(ngModel)]="draft.issuerRuc" inputmode="numeric" pattern="[0-9]{11}" maxlength="11" required>
                  <small>Debe contener exactamente 11 dígitos.</small>
                </label>
                <label>Nombre fiscal
                  <input name="issuerName" [(ngModel)]="draft.issuerName" maxlength="300" required>
                </label>
                <label>Nombre comercial
                  <input name="issuerTrade" [(ngModel)]="draft.issuerTrade" maxlength="300">
                </label>
                <label class="wide">Dirección fiscal
                  <input name="issuerAddress" [(ngModel)]="draft.issuerAddress" maxlength="500">
                </label>
                <label>Ubigeo
                  <input name="issuerUbigeo" [(ngModel)]="draft.issuerUbigeo" inputmode="numeric" pattern="[0-9]{6}" maxlength="6">
                </label>
                <label>Canal de envío
                  <select name="sendMode" [(ngModel)]="draft.sendMode" required>
                    <option value="SUNAT_DIRECT">SUNAT directo</option>
                    <option value="OSE">OSE</option>
                  </select>
                </label>
                <label>Ambiente
                  <select name="environment" [(ngModel)]="draft.environment" required>
                    <option value="BETA">Beta</option>
                    <option value="HOMOLOGATION">Homologación</option>
                    <option value="PRODUCTION">Producción</option>
                  </select>
                </label>
                <label>Serie de facturas
                  <input name="invoiceSeries" [(ngModel)]="draft.invoiceSeries" maxlength="10" required>
                </label>
                <label>Serie de boletas
                  <input name="receiptSeries" [(ngModel)]="draft.receiptSeries" maxlength="10" required>
                </label>
                <label>Serie de notas de crédito
                  <input name="creditNoteSeries" [(ngModel)]="draft.creditNoteSeries" maxlength="10" required>
                </label>
                <label>Serie de notas de débito
                  <input name="debitNoteSeries" [(ngModel)]="draft.debitNoteSeries" maxlength="10" required>
                </label>
                <label class="wide">Webhook
                  <input name="webhookUrl" [(ngModel)]="draft.webhookUrl" type="url" maxlength="500" placeholder="https://ejemplo.com/webhook">
                </label>
                <label>Máximo de documentos diarios
                  <input name="maxDocumentsPerDay" [(ngModel)]="draft.maxDocumentsPerDay" type="number" min="1" max="100000" required>
                </label>
              </div>
              <div class="panel-actions">
                <button type="button" mat-button (click)="closePanels()" [disabled]="saving">Cancelar</button>
                <button type="submit" mat-raised-button color="primary" [disabled]="tenantForm.invalid || saving">
                  <mat-icon>{{ saving ? 'hourglass_top' : 'save' }}</mat-icon>{{ saving ? 'Guardando…' : 'Guardar configuración' }}
                </button>
              </div>
            </form>
          </section>
        }

        @if (certificateTenant) {
          <section class="action-panel certificate-panel" aria-labelledby="certificate-title">
            <div class="panel-heading">
              <div>
                <h2 id="certificate-title">Certificado de {{ certificateTenant.businessName || certificateTenant.businessCode }}</h2>
                <p>El archivo se valida antes de reemplazar el certificado actual.</p>
              </div>
              <button type="button" mat-icon-button aria-label="Cerrar carga de certificado" (click)="closePanels()"><mat-icon>close</mat-icon></button>
            </div>
            <div class="certificate-grid">
              <label>Archivo PKCS#12
                <input type="file" accept=".p12,.pfx,application/x-pkcs12" (change)="selectCertificate($event)">
                <small>Formato .p12 o .pfx; máximo 5 MB.</small>
              </label>
              <label>Referencia de contraseña
                <input [(ngModel)]="passwordReference" name="passwordReference" placeholder="secret://empresa-cert-password">
                <small>La contraseña debe existir previamente en el secret store; nunca se guarda en esta pantalla.</small>
              </label>
            </div>
            @if (selectedCertificate) {
              <div class="selected-file"><mat-icon>description</mat-icon><span>{{ selectedCertificate.name }}</span><small>{{ formatBytes(selectedCertificate.size) }}</small></div>
            }
            <div class="panel-actions">
              <button type="button" mat-button (click)="closePanels()" [disabled]="uploading">Cancelar</button>
              <button type="button" mat-raised-button color="primary" (click)="uploadCertificate()"
                [disabled]="!canUploadCertificate() || uploading">
                <mat-icon>{{ uploading ? 'hourglass_top' : 'verified_user' }}</mat-icon>{{ uploading ? 'Validando…' : 'Validar y guardar' }}
              </button>
            </div>
          </section>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .breadcrumb-area { margin-bottom: 24px; }
    .breadcrumb-area h1 { margin: 0 0 4px; font-size: 22px; }
    .breadcrumb-area p { margin: 0; font-size: 13px; }
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #ffebee; color: #d32f2f; }
    .badge.active { background: #e8f5e9; color: #2e7d32; }
    .env-badge { padding: 3px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; background: #e3f2fd; color: #1565c0; }
    .env-badge[data-env="PRODUCTION"] { background: #e8f5e9; color: #2e7d32; }
    .env-badge[data-env="HOMOLOGATION"] { background: #fff3e0; color: #e65100; }
    .table-scroll { overflow-x: auto; }
    table { min-width:900px; }
    .auth-context { display:inline-flex; align-items:center; gap:7px; padding:7px 10px; color:#426845; background:#f0f8f1; border-radius:6px; font-size:12px; font-weight:700; }
    .auth-context mat-icon { width:18px; height:18px; font-size:18px; }
    .certificate-status { display:flex; align-items:center; gap:5px; color:#317039; font-size:12px; font-weight:700; }
    .certificate-status.missing { color:#9d453d; }
    .certificate-status mat-icon { width:19px; height:19px; font-size:19px; }
    .state-panel { min-height:220px; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; color:var(--mutedColor); }
    .state-panel>mat-icon { width:48px; height:48px; font-size:48px; color:var(--accentColor); }
    .state-panel h2 { margin:12px 0 4px; color:var(--forestColor); font-size:18px; }
    .state-panel p { margin:0 0 16px; }
    .state-error>mat-icon { color:#a14d45; }
    .row-actions { display: flex; gap: 2px; }
    .row-actions button { color: var(--accentStrong); }
    .row-actions button:hover:not(:disabled) { color: var(--forestColor); background: #edf6ee; }
    .action-panel { margin-top: 24px; padding: 24px; border: 1px solid var(--borderColor); border-radius: 9px; background: #f8fbf8; }
    .panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 22px; }
    .panel-heading h2 { margin: 0 0 6px; color: var(--forestColor); font-size: 20px; letter-spacing: -.02em; }
    .panel-heading p { margin: 0; color: var(--mutedColor); line-height: 1.5; }
    .form-grid, .certificate-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
    .form-grid label, .certificate-grid label { display: flex; flex-direction: column; gap: 7px; min-width: 0; color: #29482d; font-size: 13px; font-weight: 700; }
    .form-grid label.wide { grid-column: 1 / -1; }
    input, select { width: 100%; height: 46px; padding: 0 13px; border: 1px solid #b9cabd; border-radius: 7px; background: #fff; color: var(--forestColor); font: 400 15px Lato, sans-serif; outline: none; }
    input[type="file"] { height: auto; padding: 10px; }
    input:focus, select:focus { border-color: var(--accentStrong); box-shadow: 0 0 0 4px rgba(82,120,82,.14); }
    input.ng-touched.ng-invalid, select.ng-touched.ng-invalid { border-color: #b55b50; box-shadow:0 0 0 3px rgba(181,91,80,.1); }
    label small { color: var(--mutedColor); font-size: 11px; font-weight: 400; line-height: 1.4; }
    .selected-file { display: flex; align-items: center; gap: 10px; margin-top: 18px; padding: 11px 13px; border-radius: 7px; background: #edf6ee; color: var(--forestColor); }
    .selected-file small { margin-left: auto; color: var(--mutedColor); }
    .panel-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px; }
    .panel-actions button mat-icon { margin-right: 7px; }
    @media(max-width: 767px) { mat-card-header{align-items:flex-start!important;flex-direction:column;gap:10px}.action-panel { padding: 18px; } .form-grid, .certificate-grid { grid-template-columns: 1fr; } .form-grid label.wide { grid-column: auto; } .panel-heading { gap: 12px; } .panel-actions { flex-wrap: wrap; } }
  `]
})
export class ConfigComponent implements OnInit {
  displayedColumns = ['businessCode', 'businessName', 'sendMode', 'environment', 'certificate', 'status', 'actions'];
  empresas: TenantConfig[] = [];
  editingTenant: TenantConfig | null = null;
  certificateTenant: TenantConfig | null = null;
  draft: Partial<TenantConfig> | null = null;
  selectedCertificate: File | null = null;
  passwordReference = '';
  saving = false;
  uploading = false;
  loading = false;
  loadError = false;

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar) {}

  ngOnInit() {
    this.loadTenants();
  }

  loadTenants() {
    this.loading = true;
    this.loadError = false;
    this.api.listTenants().subscribe({
      next: (tenants) => { this.empresas = tenants; this.loading = false; },
      error: () => { this.loading = false; this.loadError = true; }
    });
  }

  editTenant(tenant: TenantConfig) {
    this.certificateTenant = null;
    this.editingTenant = tenant;
    this.draft = { ...tenant };
  }

  prepareCertificate(tenant: TenantConfig) {
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

  saveTenant() {
    if (!this.editingTenant || !this.draft || this.saving) return;
    this.saving = true;
    this.api.updateTenant(this.editingTenant.id, this.draft).subscribe({
      next: tenant => {
        this.replaceTenant(tenant);
        this.saving = false;
        this.closePanels();
        this.snackBar.open('Configuración guardada', 'Cerrar', { duration: 3500 });
      },
      error: error => {
        this.saving = false;
        this.snackBar.open(this.errorMessage(error, 'No se pudo guardar la configuración'), 'Cerrar', { duration: 5000 });
      }
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
    return !!this.selectedCertificate && !!this.certificateTenant && this.passwordReference.startsWith('secret://');
  }

  uploadCertificate() {
    if (!this.canUploadCertificate() || !this.selectedCertificate || !this.certificateTenant || this.uploading) return;
    this.uploading = true;
    this.api.uploadTenantCertificate(this.certificateTenant.id, this.selectedCertificate, this.passwordReference.trim()).subscribe({
      next: tenant => {
        this.replaceTenant(tenant);
        this.uploading = false;
        this.closePanels();
        this.snackBar.open(`Certificado vigente hasta ${tenant.certificateValidTo}`, 'Cerrar', { duration: 5000 });
      },
      error: error => {
        this.uploading = false;
        this.snackBar.open(this.errorMessage(error, 'No se pudo validar el certificado'), 'Cerrar', { duration: 6000 });
      }
    });
  }

  isBusy(tenantId: string): boolean {
    return (this.saving && this.editingTenant?.id === tenantId) || (this.uploading && this.certificateTenant?.id === tenantId);
  }

  formatBytes(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  private replaceTenant(tenant: TenantConfig) {
    this.empresas = this.empresas.map(current => current.id === tenant.id ? tenant : current);
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') return error.error.error;
    return fallback;
  }
}
