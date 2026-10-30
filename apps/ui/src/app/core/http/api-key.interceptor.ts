import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { EMPTY, NEVER, catchError, filter, takeUntil, throwError, timeout } from 'rxjs';
import { AuthService } from '../session/auth.service';
import { SessionContextService } from '../session/session-context.service';
import { isCaraboApi } from './api-boundary';
export const apiKeyInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isCaraboApi(request.url)) return next(request);
  const auth = inject(AuthService),
    context = inject(SessionContextService),
    snapshot = context.snapshot(),
    token = auth.accessToken(),
    businessCode = auth.session()?.tenantId;
  const discovery = /\/access\/businesses(?:[/?#]|$)/.test(request.url);
  // A new login's discovery carries its own credential before the session is installed.
  if (discovery) return next(request).pipe(timeout(15000));
  if (token)
    request = request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
        ...(businessCode ? { 'business-code': businessCode } : {}),
      },
    });
  return next(request).pipe(
    timeout(30000),
    takeUntil(
      ['GET', 'HEAD'].includes(request.method)
        ? context.changes$.pipe(filter(() => !context.isCurrent(snapshot)))
        : NEVER,
    ),
    filter(() => context.isCurrent(snapshot)),
    catchError((error: unknown) => {
      if (!context.isCurrent(snapshot)) return EMPTY;
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        auth.accessToken() === token &&
        context.isCurrent(snapshot)
      )
        auth.requireReauthentication();
      return throwError(() => error);
    }),
  );
};
