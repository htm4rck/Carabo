import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { BatchResult } from '../domain/batch.models';
@Injectable({ providedIn: 'root' })
export class BatchApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  generateSummary(businessCode: string, date?: string): Observable<BatchResult> {
    return this.http.post<BatchResult>(`${this.baseUrl}/documents/batch/summary`, {
      businessCode,
      date,
    });
  }

  sendVoided(businessCode: string, documentIds: string[], reason: string): Observable<BatchResult> {
    return this.http.post<BatchResult>(`${this.baseUrl}/documents/batch/void`, {
      businessCode,
      documentIds,
      reason,
    });
  }

  checkTicket(businessCode: string, ticket: string): Observable<BatchResult> {
    return this.http.get<BatchResult>(
      `${this.baseUrl}/documents/batch/ticket/${businessCode}/${ticket}`,
    );
  }

  listBatchOperations(kind?: 'RC' | 'RA', limit = 50): Observable<BatchResult[]> {
    let params = new HttpParams().set('limit', String(limit));
    if (kind) params = params.set('kind', kind);
    return this.http.get<BatchResult[]>(`${this.baseUrl}/documents/batch/operations`, { params });
  }

  downloadBatchCdr(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/documents/batch/operations/${id}/cdr`, {
      responseType: 'blob',
    });
  }
}
