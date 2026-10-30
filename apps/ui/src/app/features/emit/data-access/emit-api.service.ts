import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class EmitApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  emitDocument(payload: any): Observable<any> {
    const idempotencyKey = payload.externalId || crypto.randomUUID();
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post(`${this.baseUrl}/documents/emit`, payload, { headers });
  }
}
