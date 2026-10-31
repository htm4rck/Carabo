import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import {
  Component,
  HostListener,
  OnInit,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/session/auth.service';
import { IntegrationsApi as CaraboApiService } from '../data-access/integrations-api.service';
import type {
  ProducerCredential,
  ProducerIntegration,
  ProducerScope,
} from '../domain/integrations.models';
import { environment } from '../../../../environments/environment';
import { IntegrationGuideDialogComponent } from '../ui/integration-guide-dialog.component';

@Component({
  selector: 'app-integrations',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatSnackBarModule,
  ],
  templateUrl: './integrations.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './integrations.component.scss',
})
export class IntegrationsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  producers: ProducerIntegration[] = [];
  loading = false;
  saving = false;
  creating = false;
  busyId = '';
  error = '';
  credential: ProducerCredential | null = null;
  credentialStored = false;
  draft = { code: '', name: '', description: '' };
  scopeSelection: Record<ProducerScope, boolean> = {
    DOCUMENT_INGEST: true,
    DOCUMENT_READ: true,
    DOCUMENT_EMIT: false,
  };
  constructor(
    private api: CaraboApiService,
    public auth: AuthService,
    private snack: MatSnackBar,
    private dialog: MatDialog,
  ) {}
  ngOnInit(): void {
    this.load();
  }
  @HostListener('window:beforeunload', ['$event'])
  protectPendingCredential(event: BeforeUnloadEvent): void {
    if (this.credential && !this.credentialStored) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
  canLeaveWithCredential(): boolean {
    if (!this.credential || this.credentialStored) return true;
    this.snack.open(
      'Confirma “Ya la guardé” antes de salir para no perder la credencial.',
      'Cerrar',
      { duration: 5000 },
    );
    return false;
  }
  load(): void {
    this.loading = true;
    this.error = '';
    this.api
      .listProducerIntegrations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (v) => {
          this.producers = v;
          this.loading = false;
        },
        error: () => {
          this.error = 'No pudimos consultar las integraciones de la empresa activa.';
          this.loading = false;
        },
      });
  }
  get selectedScopes(): ProducerScope[] {
    return (Object.keys(this.scopeSelection) as ProducerScope[]).filter(
      (scope) => this.scopeSelection[scope],
    );
  }
  get activeCount(): number {
    return this.producers.filter((p) => p.status === 'ACTIVE').length;
  }
  get degradedCount(): number {
    return this.producers.filter((p) => p.health === 'DEGRADED').length;
  }
  get revokedCount(): number {
    return this.producers.filter((p) => p.status === 'REVOKED').length;
  }
  create(): void {
    if (this.saving || this.credential || this.selectedScopes.length === 0) return;
    this.saving = true;
    this.api
      .createProducerIntegration({ ...this.draft, scopes: this.selectedScopes })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.showCredential(result);
          this.producers = [result.producer, ...this.producers];
          this.draft = { code: '', name: '', description: '' };
          this.resetScopes();
          this.creating = false;
          this.saving = false;
        },
        error: (error) => {
          this.saving = false;
          this.snack.open(error?.error?.error || 'No se pudo crear el productor', 'Cerrar', {
            duration: 5000,
          });
        },
      });
  }
  rotate(producer: ProducerIntegration): void {
    if (
      this.busyId ||
      this.credential ||
      !window.confirm(
        `Rotar la credencial de ${producer.name} invalidará inmediatamente la clave anterior. Confirma que puedes actualizar el secret store del productor.`,
      )
    )
      return;
    this.busyId = producer.id;
    this.api
      .rotateProducerCredential(producer.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.showCredential(result);
          this.replace(result.producer);
          this.busyId = '';
        },
        error: () => {
          this.busyId = '';
          this.snack.open('No se pudo rotar la credencial', 'Cerrar', { duration: 4000 });
        },
      });
  }
  toggle(producer: ProducerIntegration): void {
    const activating = producer.status !== 'ACTIVE';
    const impact = activating
      ? 'volverá a permitir solicitudes de esta aplicación'
      : `detendrá inmediatamente las solicitudes de ${producer.name}`;
    if (
      this.busyId ||
      this.credential ||
      !window.confirm(
        `${activating ? 'Activar' : 'Revocar'} ${producer.name} ${impact}. ¿Deseas continuar?`,
      )
    )
      return;
    this.busyId = producer.id;
    this.api
      .setProducerIntegrationActive(producer.id, activating)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.replace(updated);
          this.busyId = '';
        },
        error: () => {
          this.busyId = '';
          this.snack.open('No se pudo cambiar el estado', 'Cerrar', { duration: 4000 });
        },
      });
  }
  copyCredential(): void {
    if (!this.credential) return;
    const clipboard = navigator.clipboard;
    if (!clipboard) {
      this.snack.open(
        'El portapapeles no está disponible. Copia manualmente la credencial.',
        'Cerrar',
        { duration: 4500 },
      );
      return;
    }
    clipboard
      .writeText(this.credential.apiKey)
      .then(() =>
        this.snack.open('Credencial copiada. Guárdala ahora en el secret store.', 'Cerrar', {
          duration: 3500,
        }),
      )
      .catch(() =>
        this.snack.open(
          'El navegador rechazó el portapapeles. Selecciona y copia la credencial manualmente.',
          'Cerrar',
          { duration: 5000 },
        ),
      );
  }
  showCredential(value: ProducerCredential): void {
    this.credential = value;
    this.credentialStored = false;
  }
  dismissCredential(): void {
    if (!this.credentialStored) return;
    this.credential = null;
    this.credentialStored = false;
  }
  resetScopes(): void {
    this.scopeSelection = { DOCUMENT_INGEST: true, DOCUMENT_READ: true, DOCUMENT_EMIT: false };
  }
  openGuide(producer: ProducerIntegration | null): void {
    const apiBaseUrl = new URL(environment.apiUrl, window.location.origin)
      .toString()
      .replace(/\/$/, '');
    this.dialog.open(IntegrationGuideDialogComponent, {
      data: { producer, apiBaseUrl, canAdminister: this.auth.canAdminister() },
      autoFocus: 'dialog',
      restoreFocus: true,
      maxWidth: 'none',
      panelClass: 'carabo-integration-dialog',
    });
  }
  replace(updated: ProducerIntegration): void {
    this.producers = this.producers.map((item) => (item.id === updated.id ? updated : item));
  }
  healthLabel(value: ProducerIntegration['health']): string {
    return (
      {
        HEALTHY: 'Operativa',
        PENDING: 'Pendiente de conexión',
        IDLE: 'Sin actividad reciente',
        DEGRADED: 'Requiere atención',
        REVOKED: 'Revocada',
      } as Record<string, string>
    )[value];
  }
  scopeLabel(value: ProducerScope): string {
    return (
      {
        DOCUMENT_INGEST: 'Enviar',
        DOCUMENT_READ: 'Consultar',
        DOCUMENT_EMIT: 'Síncrona/GRE',
      } as Record<ProducerScope, string>
    )[value];
  }
}
