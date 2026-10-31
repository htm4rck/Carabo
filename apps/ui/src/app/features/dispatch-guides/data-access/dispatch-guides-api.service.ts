import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { DispatchGuideSummary } from '../domain/dispatch-guides.models';
@Injectable({ providedIn: 'root' })
export class DispatchGuidesApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  listDispatchGuides(limit = 100): Observable<DispatchGuideSummary[]> {
    return this.http.get<DispatchGuideSummary[]>(`${this.baseUrl}/dispatch-guides`, {
      params: { limit: String(limit) },
    });
  }

  checkDispatchGuideStatus(id: string): Observable<DispatchGuideSummary> {
    return this.http.post<DispatchGuideSummary>(`${this.baseUrl}/dispatch-guides/${id}/status`, {});
  }

  retryDispatchGuide(id: string): Observable<DispatchGuideSummary> {
    return this.http.post<DispatchGuideSummary>(`${this.baseUrl}/dispatch-guides/${id}/retry`, {});
  }

  downloadDispatchGuide(id: string, type: 'xml' | 'cdr' | 'pdf'): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/dispatch-guides/${id}/${type}`, { responseType: 'blob' });
  }
}
