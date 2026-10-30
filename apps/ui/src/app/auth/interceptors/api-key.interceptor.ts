import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth.service';

export const apiKeyInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  if (/\/auth\/login(?:[/?#]|$)/.test(request.url)) return next(request);
  const token = auth.accessToken();
  const businessCode = auth.session()?.tenantId;
  const discovery = /\/access\/businesses(?:[/?#]|$)/.test(request.url);
  if (token && !discovery) request = request.clone({ setHeaders: {
    Authorization: `Bearer ${token}`,
    ...(businessCode ? { 'business-code': businessCode } : {}),
  }});
  return next(request).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401) auth.requireReauthentication();
    return throwError(() => error);
  }));
};
