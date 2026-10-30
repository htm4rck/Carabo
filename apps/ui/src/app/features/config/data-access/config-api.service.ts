import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { TenantConfig, FiscalReadiness } from '../domain/config.models';
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
  ): Observable<TenantConfig> {
    const form = new FormData();
    form.append('certificate', certificate, certificate.name);
    form.append('passwordReference', passwordReference);
    return this.http.post<TenantConfig>(`${this.baseUrl}/tenants/${id}/certificate`, form);
  }
}
