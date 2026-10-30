import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { NotificationSummary, AsyncMessageSummary } from '../domain/operations.models';
@Injectable({ providedIn: 'root' })
export class OperationsApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  listNotifications(limit = 100): Observable<NotificationSummary[]> {
    return this.http.get<NotificationSummary[]>(`${this.baseUrl}/operations/notifications`, {
      params: { limit: String(limit) },
    });
  }

  retryNotification(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/operations/notifications/${id}/retry`, {});
  }

  listAsyncMessages(status?: string, limit = 100): Observable<AsyncMessageSummary[]> {
    let params = new HttpParams().set('limit', String(limit));
    if (status) params = params.set('status', status);
    return this.http.get<AsyncMessageSummary[]>(`${this.baseUrl}/operations/messages`, { params });
  }

  replayAsyncMessage(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/operations/messages/${id}/replay`, {});
  }
}
