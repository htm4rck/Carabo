import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { FiscalStatus } from '../domain/fiscal-status.models';
@Injectable({ providedIn: 'root' })
export class FiscalStatusApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  getFiscalStatus(): Observable<FiscalStatus> {
    return this.http.get<FiscalStatus>(`${this.baseUrl}/fiscal-status`);
  }
}
