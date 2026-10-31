import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type {
  TenantConfig,
  FiscalReadiness,
  CredentialStatus,
  CredentialValues,
} from '../domain/config.models';
@Injectable({ providedIn: 'root' })
export class ConfigApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  getTenant(businessCode: string): Observable<TenantConfig> {
    return this.http.get<TenantConfig>(`${this.baseUrl}/tenants/${businessCode}`);
  }

  listTenants(): Observable<TenantConfig[]> {
    return this.http.get<TenantConfig[]>(`${this.baseUrl}/tenants`);
  }

  getFiscalReadiness(businessCode: string): Observable<FiscalReadiness> {
    return this.http.get<FiscalReadiness>(
      `${this.baseUrl}/tenants/${encodeURIComponent(businessCode)}/readiness`,
    );
  }

  createTenant(data: Partial<TenantConfig>): Observable<TenantConfig> {
    return this.http.post<TenantConfig>(`${this.baseUrl}/tenants`, data);
  }

  updateTenant(id: string, data: Partial<TenantConfig>): Observable<TenantConfig> {
    return this.http.put<TenantConfig>(`${this.baseUrl}/tenants/${id}`, data);
  }

  uploadTenantCertificate(
    id: string,
    certificate: File,
    passwordReference: string,
    password = '',
  ): Observable<TenantConfig> {
    const form = new FormData();
    form.append('certificate', certificate, certificate.name);
    if (passwordReference) form.append('passwordReference', passwordReference);
    if (password) form.append('password', password);
    return this.http.post<TenantConfig>(`${this.baseUrl}/tenants/${id}/certificate`, form);
  }
  credentialStatus(id: string): Observable<CredentialStatus> {
    return this.http.get<CredentialStatus>(`${this.baseUrl}/tenants/${id}/credentials`);
  }
  saveCredentials(id: string, values: CredentialValues): Observable<CredentialStatus> {
    return this.http.post<CredentialStatus>(`${this.baseUrl}/tenants/${id}/credentials`, values);
  }
  importCredentials(id: string, file: File): Observable<CredentialStatus> {
    const body = new FormData();
    body.append('credentials', file, file.name);
    return this.http.post<CredentialStatus>(
      `${this.baseUrl}/tenants/${id}/credentials/import`,
      body,
    );
  }
  credentialTemplate(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/tenants/${id}/credentials/template`, {
      responseType: 'blob',
    });
  }
}
