import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DocumentSummary {
  id: string;
  fullNumber: string;
  docType: string;
  docTypeName: string;
  status: string;
  total: number;
  currency: string;
  receiverName: string;
  receiverDoc: string;
  issueDate: string;
  hashCode: string;
  externalId: string;
  businessCode: string;
  sunatResponse: string;
  createdAt: string;
  timeZone: string;
}

export interface QueryResponse {
  items: DocumentSummary[];
  total: number;
  offset: number;
  limit: number;
}

export interface QueryParams {
  businessCode?: string;
  docType?: string;
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  receiverDoc?: string;
  series?: string;
  search?: string;
  offset?: number;
  limit?: number;
}

export interface TenantConfig {
  id: string;
  businessCode: string;
  businessName: string;
  issuerRuc: string;
  issuerName: string;
  issuerTrade: string;
  issuerAddress: string;
  issuerUbigeo: string;
  sendMode: string;
  sunatEndpoint: string;
  solUser?: string;
  solPassword?: string;
  oseBaseUrl: string;
  certificateFile: string;
  certificateValidFrom: string;
  certificateValidTo: string;
  invoiceSeries: string;
  receiptSeries: string;
  creditNoteSeries: string;
  debitNoteSeries: string;
  environment: string;
  webhookUrl: string;
  maxDocumentsPerDay: number;
  active: boolean;
  countryCode: string;
}

export interface FiscalReadinessCheck {
  code: string;
  label: string;
  ready: boolean;
  required: boolean;
  detail: string;
}

export interface FiscalReadiness {
  businessCode: string;
  environment: string;
  sendMode: string;
  ready: boolean;
  blockers: number;
  checks: FiscalReadinessCheck[];
}

export interface SalesReport {
  businessCode: string;
  from: string;
  to: string;
  totalDocuments: number;
  totalTaxBase: number;
  totalIgv: number;
  totalAmount: number;
  byType: { docType: string; count: number; taxBase: number; igvAmount: number; total: number }[];
}

export interface DashboardStats {
  emitted: number;
  accepted: number;
  rejected: number;
  pending: number;
  tenants: number;
}

export interface BatchResult {
  id: string | null;
  kind: 'RC' | 'RA' | null;
  identifier: string | null;
  success: boolean;
  ticket: string | null;
  status: 'EMPTY' | 'SENT' | 'PENDING' | 'ACCEPTED' | 'OBSERVED' | 'REJECTED' | 'ERROR' | string;
  message: string;
  documentCount: number;
  referenceDate: string | null;
  generatedAt: string;
  timeZone: string;
}

export interface DispatchGuideSummary {
  id: string;
  fullNumber: string;
  status: string;
  issueDate: string;
  startDate: string;
  receiverName: string;
  receiverDoc: string;
  transportMode: string;
  originAddress: string;
  destinationAddress: string;
  transferReasonCode: string;
  externalId: string;
  hashCode: string;
  ticket: string | null;
  sunatResponse: string | null;
}

export interface NotificationSummary {
  id: string;
  documentId: string;
  type: 'CALLBACK' | 'WEBHOOK';
  status: 'PENDING' | 'RETRY' | 'DELIVERED' | 'DEAD';
  attempts: number;
  lastError: string | null;
  createdAt: string;
  deliveredAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class CaraboApiService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // --- Documents ---

  queryDocuments(params: QueryParams): Observable<QueryResponse> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<QueryResponse>(`${this.baseUrl}/documents`, { params: httpParams });
  }

  getDocument(id: string): Observable<DocumentSummary> {
    return this.http.get<DocumentSummary>(`${this.baseUrl}/documents/${id}`);
  }

  retryDocument(id: string): Observable<DocumentSummary> {
    return this.http.post<DocumentSummary>(`${this.baseUrl}/documents/${id}/retry`, {});
  }

  getDocumentXmlUrl(id: string): string {
    return `${this.baseUrl}/documents/${id}/xml`;
  }

  getDocumentCdrUrl(id: string): string {
    return `${this.baseUrl}/documents/${id}/cdr`;
  }

  getDocumentPdfUrl(id: string, format?: string): string {
    return `${this.baseUrl}/documents/${id}/pdf${format ? '?format=' + format : ''}`;
  }

  downloadDocumentXml(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/documents/${id}/xml`, { responseType: 'blob' });
  }

  downloadDocumentCdr(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/documents/${id}/cdr`, { responseType: 'blob' });
  }

  downloadDocumentPdf(id: string, format?: string): Observable<Blob> {
    const params = format ? new HttpParams().set('format', format) : undefined;
    return this.http.get(`${this.baseUrl}/documents/${id}/pdf`, { params, responseType: 'blob' });
  }

  emitDocument(payload: any): Observable<any> {
    const idempotencyKey = payload.externalId || crypto.randomUUID();
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post(`${this.baseUrl}/documents/emit`, payload, { headers });
  }

  // --- Tenants ---

  getTenant(businessCode: string): Observable<TenantConfig> {
    return this.http.get<TenantConfig>(`${this.baseUrl}/tenants/${businessCode}`);
  }

  listTenants(): Observable<TenantConfig[]> {
    return this.http.get<TenantConfig[]>(`${this.baseUrl}/tenants`);
  }

  getFiscalReadiness(businessCode: string): Observable<FiscalReadiness> {
    return this.http.get<FiscalReadiness>(`${this.baseUrl}/tenants/${encodeURIComponent(businessCode)}/readiness`);
  }

  createTenant(data: Partial<TenantConfig>): Observable<TenantConfig> {
    return this.http.post<TenantConfig>(`${this.baseUrl}/tenants`, data);
  }

  updateTenant(id: string, data: Partial<TenantConfig>): Observable<TenantConfig> {
    return this.http.put<TenantConfig>(`${this.baseUrl}/tenants/${id}`, data);
  }

  uploadTenantCertificate(id: string, certificate: File, passwordReference: string): Observable<TenantConfig> {
    const form = new FormData();
    form.append('certificate', certificate, certificate.name);
    form.append('passwordReference', passwordReference);
    return this.http.post<TenantConfig>(`${this.baseUrl}/tenants/${id}/certificate`, form);
  }

  // --- Batch ---

  generateSummary(businessCode: string, date?: string): Observable<BatchResult> {
    return this.http.post<BatchResult>(`${this.baseUrl}/documents/batch/summary`, { businessCode, date });
  }

  sendVoided(businessCode: string, documentIds: string[], reason: string): Observable<BatchResult> {
    return this.http.post<BatchResult>(`${this.baseUrl}/documents/batch/void`, { businessCode, documentIds, reason });
  }

  checkTicket(businessCode: string, ticket: string): Observable<BatchResult> {
    return this.http.get<BatchResult>(`${this.baseUrl}/documents/batch/ticket/${businessCode}/${ticket}`);
  }

  listBatchOperations(kind?: 'RC' | 'RA', limit = 50): Observable<BatchResult[]> {
    let params = new HttpParams().set('limit', String(limit));
    if (kind) params = params.set('kind', kind);
    return this.http.get<BatchResult[]>(`${this.baseUrl}/documents/batch/operations`, { params });
  }

  downloadBatchCdr(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/documents/batch/operations/${id}/cdr`, { responseType: 'blob' });
  }

  // --- Reports ---

  salesReport(businessCode: string, from: string, to: string): Observable<SalesReport> {
    return this.http.get<SalesReport>(`${this.baseUrl}/reports/sales`, {
      params: { businessCode, from, to }
    });
  }

  monthlyReport(businessCode: string, year: number, month: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/reports/monthly`, {
      params: { businessCode, year: String(year), month: String(month) }
    });
  }

  downloadSalesExcel(from: string, to: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reports/export/excel`, {
      params: { from, to }, responseType: 'blob'
    });
  }

  // --- Dispatch guides ---

  listDispatchGuides(limit = 100): Observable<DispatchGuideSummary[]> {
    return this.http.get<DispatchGuideSummary[]>(`${this.baseUrl}/dispatch-guides`, {
      params: { limit: String(limit) }
    });
  }

  checkDispatchGuideStatus(id: string): Observable<DispatchGuideSummary> {
    return this.http.post<DispatchGuideSummary>(`${this.baseUrl}/dispatch-guides/${id}/status`, {});
  }

  downloadDispatchGuide(id: string, type: 'xml' | 'cdr' | 'pdf'): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/dispatch-guides/${id}/${type}`, { responseType: 'blob' });
  }

  listNotifications(limit = 100): Observable<NotificationSummary[]> {
    return this.http.get<NotificationSummary[]>(`${this.baseUrl}/operations/notifications`, {
      params: { limit: String(limit) }
    });
  }
}
