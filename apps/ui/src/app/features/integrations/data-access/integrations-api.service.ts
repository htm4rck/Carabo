import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type {
  IntegrationValidationReport,
  ProducerIntegration,
  ProducerScope,
  ProducerCredential,
} from '../domain/integrations.models';
@Injectable({ providedIn: 'root' })
export class IntegrationsApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  validateIntegrationPayload(
    payload: unknown,
    idempotencyKey: string,
  ): Observable<IntegrationValidationReport> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<IntegrationValidationReport>(
      `${this.baseUrl}/documents/validate`,
      payload,
      { headers },
    );
  }

  validateDispatchGuidePayload(
    payload: unknown,
    idempotencyKey: string,
  ): Observable<IntegrationValidationReport> {
    return this.http.post<IntegrationValidationReport>(
      `${this.baseUrl}/dispatch-guides/validate`,
      payload,
      {
        headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
      },
    );
  }

  listProducerIntegrations(): Observable<ProducerIntegration[]> {
    return this.http.get<ProducerIntegration[]>(`${this.baseUrl}/integrations`);
  }

  createProducerIntegration(data: {
    code: string;
    name: string;
    description?: string;
    scopes: ProducerScope[];
  }): Observable<ProducerCredential> {
    return this.http.post<ProducerCredential>(`${this.baseUrl}/integrations`, data);
  }

  rotateProducerCredential(id: string): Observable<ProducerCredential> {
    return this.http.post<ProducerCredential>(
      `${this.baseUrl}/integrations/${encodeURIComponent(id)}/rotate`,
      {},
    );
  }

  setProducerIntegrationActive(id: string, active: boolean): Observable<ProducerIntegration> {
    return this.http.post<ProducerIntegration>(
      `${this.baseUrl}/integrations/${encodeURIComponent(id)}/status`,
      { active },
    );
  }
}
