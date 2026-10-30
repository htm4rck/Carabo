import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Component, Inject, inject, DestroyRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { IntegrationsApi as CaraboApiService } from '../data-access/integrations-api.service';
import type {
  IntegrationValidationReport,
  ProducerIntegration,
} from '../domain/integrations.models';
import {
  DOCUMENT_TYPE_LABELS,
  IntegrationDocumentType,
  exampleExternalId,
  integrationExample,
} from '../domain/integration-examples';
import { CodeBlockComponent } from './code-block.component';

export interface IntegrationGuideData {
  producer: ProducerIntegration | null;
  apiBaseUrl: string;
  canAdminister: boolean;
}

@Component({
  selector: 'app-integration-guide-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatSnackBarModule,
    CodeBlockComponent,
  ],
  templateUrl: './integration-guide-dialog.component.html',
  styleUrl: './integration-guide-dialog.component.scss',
})
export class IntegrationGuideDialogComponent {
  private readonly destroyRef = inject(DestroyRef);

  section: 'credential' | 'transport' | 'payload' | 'validator' = 'credential';
  transport: 'http' | 'rabbit' = 'http';
  docType: IntegrationDocumentType = '01';
  payloadText = '';
  validationKey = '';
  validating = false;
  parseError = '';
  report: IntegrationValidationReport | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: IntegrationGuideData,
    private api: CaraboApiService,
    private snack: MatSnackBar,
  ) {
    this.loadReference();
  }

  get externalId(): string {
    return exampleExternalId(this.docType);
  }
  get documentLabel(): string {
    return DOCUMENT_TYPE_LABELS[this.docType];
  }
  get referenceText(): string {
    return JSON.stringify(integrationExample(this.docType), null, 2);
  }
  get isDispatchGuide(): boolean {
    return this.docType === '09';
  }
  get validationPath(): string {
    return this.isDispatchGuide ? '/dispatch-guides/validate' : '/documents/validate';
  }
  get emissionPath(): string {
    return this.isDispatchGuide ? '/dispatch-guides/emit' : '/documents/ingest';
  }
  get secretExample(): string {
    return `# Secret store / variable del backend productor\nCARABO_API_KEY=<credencial revelada por Carabo>\nCARABO_BASE_URL=${this.data.apiBaseUrl}`;
  }
  get curlExample(): string {
    const body = this.referenceText.replaceAll("'", "'\\''");
    return `curl --request POST '${this.data.apiBaseUrl}${this.emissionPath}' \\\n  --header 'X-Api-Key: <CARABO_API_KEY>' \\\n  --header 'Idempotency-Key: ${this.externalId}' \\\n  --header 'Content-Type: application/json' \\\n  --data '${body}'`;
  }
  get errorCount(): number {
    return this.report?.issues.filter((issue) => issue.severity === 'ERROR').length || 0;
  }
  get warningCount(): number {
    return this.report?.issues.filter((issue) => issue.severity === 'WARNING').length || 0;
  }

  changeType(): void {
    this.report = null;
    this.parseError = '';
    this.loadReference();
  }
  clearValidation(): void {
    this.report = null;
    this.parseError = '';
  }
  loadReference(): void {
    this.payloadText = this.referenceText;
    this.validationKey = this.externalId;
    this.report = null;
    this.parseError = '';
  }
  validate(): void {
    this.parseError = '';
    this.report = null;
    let payload: unknown;
    try {
      payload = JSON.parse(this.payloadText);
    } catch (error) {
      this.parseError = error instanceof Error ? error.message : 'Revisa comas, llaves y comillas.';
      return;
    }
    if (payload && typeof payload === 'object' && 'docType' in payload) {
      const detectedType = String((payload as Record<string, unknown>)['docType']);
      if (['01', '03', '07', '08', '09'].includes(detectedType))
        this.docType = detectedType as IntegrationDocumentType;
    }
    if (
      payload &&
      typeof payload === 'object' &&
      !('docType' in payload) &&
      ('transportMode' in payload || 'origin' in payload || 'destination' in payload)
    )
      this.docType = '09';
    this.validating = true;
    const request = this.isDispatchGuide
      ? this.api.validateDispatchGuidePayload(payload, this.validationKey)
      : this.api.validateIntegrationPayload(payload, this.validationKey);
    request
      .pipe(finalize(() => (this.validating = false)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (report) => (this.report = report),
        error: (error) => {
          const message =
            error?.status === 403
              ? `La credencial no tiene ${this.isDispatchGuide ? 'DOCUMENT_READ' : 'DOCUMENT_INGEST'}.`
              : 'No fue posible validar. Revisa la conexión y vuelve a intentar.';
          this.snack.open(message, 'Cerrar', { duration: 5000 });
        },
      });
  }
  copy(value: string, message: string): void {
    if (!navigator.clipboard) {
      this.snack.open('Selecciona y copia el contenido manualmente.', 'Cerrar', { duration: 4000 });
      return;
    }
    navigator.clipboard
      .writeText(value)
      .then(() => this.snack.open(message, 'Cerrar', { duration: 2200 }))
      .catch(() =>
        this.snack.open('El navegador rechazó el portapapeles. Copia manualmente.', 'Cerrar', {
          duration: 4500,
        }),
      );
  }
}
