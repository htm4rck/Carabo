import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaraboApiService, TenantConfig } from '../../services/carabo-api.service';

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatChipsModule, MatDialogModule, MatSnackBarModule],
  template: `
    <div class="breadcrumb-area">
      <h1>Configuración de Empresas</h1>
      <p class="gray-color">Gestión de facturación electrónica por empresa</p>
    </div>

    <mat-card>
      <mat-card-header class="d-flex align-items-center justify-content-space-between" style="padding:20px 20px 0;">
        <h3 style="margin:0; font-size:16px;">Empresas configuradas</h3>
        <button mat-raised-button color="primary">
          <mat-icon>add</mat-icon> Nueva empresa
        </button>
      </mat-card-header>
      <mat-card-content style="padding:20px;">
        <table mat-table [dataSource]="empresas" class="w-100">
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
              <mat-icon [style.color]="e.certificateFile ? '#388e3c' : '#d32f2f'">
                {{ e.certificateFile ? 'verified' : 'warning' }}
              </mat-icon>
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
              <button mat-icon-button matTooltip="Configurar"><mat-icon>settings</mat-icon></button>
              <button mat-icon-button matTooltip="Subir certificado"><mat-icon>upload_file</mat-icon></button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        @if (empresas.length === 0) {
          <div style="text-align:center; padding:40px;">
            <mat-icon style="font-size:48px; width:48px; height:48px; color:#ccc;">business</mat-icon>
            <p class="gray-color">No hay empresas configuradas. Agrega una para comenzar.</p>
          </div>
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
  `]
})
export class ConfigComponent implements OnInit {
  displayedColumns = ['businessCode', 'businessName', 'sendMode', 'environment', 'certificate', 'status', 'actions'];
  empresas: TenantConfig[] = [];

  constructor(private api: CaraboApiService, private snackBar: MatSnackBar) {}

  ngOnInit() {
    this.api.listTenants().subscribe({
      next: (tenants) => this.empresas = tenants,
      error: () => this.snackBar.open('Error al cargar empresas', 'OK', { duration: 3000 })
    });
  }
}
