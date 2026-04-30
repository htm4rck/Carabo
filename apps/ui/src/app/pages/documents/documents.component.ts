import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatFormFieldModule, MatSelectModule, MatInputModule],
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
            <mat-select>
              <mat-option value="">Todos</mat-option>
              <mat-option value="01">Factura</mat-option>
              <mat-option value="03">Boleta</mat-option>
              <mat-option value="07">Nota de Crédito</mat-option>
              <mat-option value="08">Nota de Débito</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Estado</mat-label>
            <mat-select>
              <mat-option value="">Todos</mat-option>
              <mat-option value="PENDING">Pendiente</mat-option>
              <mat-option value="SIGNED">Firmado</mat-option>
              <mat-option value="SENT">Enviado</mat-option>
              <mat-option value="ACCEPTED">Aceptado</mat-option>
              <mat-option value="REJECTED">Rechazado</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Buscar</mat-label>
            <input matInput placeholder="Serie, correlativo, RUC...">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
        </div>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-content style="padding:20px;">
        <table mat-table [dataSource]="documents" class="w-100">
          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>Tipo</th>
            <td mat-cell *matCellDef="let d">{{ d.type }}</td>
          </ng-container>
          <ng-container matColumnDef="series">
            <th mat-header-cell *matHeaderCellDef>Serie-Correlativo</th>
            <td mat-cell *matCellDef="let d">{{ d.series }}-{{ d.correlative }}</td>
          </ng-container>
          <ng-container matColumnDef="customer">
            <th mat-header-cell *matHeaderCellDef>Cliente</th>
            <td mat-cell *matCellDef="let d">{{ d.customer }}</td>
          </ng-container>
          <ng-container matColumnDef="total">
            <th mat-header-cell *matHeaderCellDef>Total</th>
            <td mat-cell *matCellDef="let d">{{ d.currency }} {{ d.total }}</td>
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
              <button mat-icon-button matTooltip="Ver XML"><mat-icon>code</mat-icon></button>
              <button mat-icon-button matTooltip="Descargar CDR"><mat-icon>download</mat-icon></button>
              <button mat-icon-button matTooltip="Reintentar"><mat-icon>refresh</mat-icon></button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        @if (documents.length === 0) {
          <div style="text-align:center; padding:40px;">
            <mat-icon style="font-size:48px; width:48px; height:48px; color:#ccc;">description</mat-icon>
            <p class="gray-color">No hay documentos emitidos aún.</p>
          </div>
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
    .badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #e3f2fd; color: #1565c0; }
    .badge[data-status="ACCEPTED"] { background: #e8f5e9; color: #2e7d32; }
    .badge[data-status="REJECTED"] { background: #ffebee; color: #c62828; }
    .badge[data-status="PENDING"] { background: #fff3e0; color: #e65100; }
    .badge[data-status="ERROR"] { background: #fce4ec; color: #b71c1c; }
  `]
})
export class DocumentsComponent {
  displayedColumns = ['type', 'series', 'customer', 'total', 'status', 'actions'];
  documents: any[] = [];
}
