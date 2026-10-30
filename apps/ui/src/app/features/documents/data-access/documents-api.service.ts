import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { QueryParams, QueryResponse, DocumentSummary } from '../domain/documents.models';
@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
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
}
