import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UI_BUILD } from '../../../environments/build-info';

export interface BuildInfo {
  version: string;
  buildDate: string;
  commit: string;
}

@Injectable({ providedIn: 'root' })
export class BuildInfoService {
  private readonly http = inject(HttpClient);
  readonly ui = UI_BUILD;
  readonly api = signal<BuildInfo>({ version: 'no disponible', buildDate: '', commit: '' });

  load(): void {
    this.http
      .get<BuildInfo>(`${environment.apiUrl}/version`)
      .pipe(catchError(() => of({ version: 'no disponible', buildDate: '', commit: '' })))
      .subscribe((info) => this.api.set(info));
  }
}
