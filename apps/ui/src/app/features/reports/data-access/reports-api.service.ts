import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { SalesReport } from '../domain/reports.models';
@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly baseUrl = environment.apiUrl;
  constructor(private readonly http: HttpClient) {}
  salesReport(businessCode: string, from: string, to: string): Observable<SalesReport> {
    return this.http.get<SalesReport>(`${this.baseUrl}/reports/sales`, {
      params: { businessCode, from, to },
    });
  }

  monthlyReport(businessCode: string, year: number, month: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/reports/monthly`, {
      params: { businessCode, year: String(year), month: String(month) },
    });
  }

  downloadSalesExcel(from: string, to: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reports/export/excel`, {
      params: { from, to },
      responseType: 'blob',
    });
  }
}
