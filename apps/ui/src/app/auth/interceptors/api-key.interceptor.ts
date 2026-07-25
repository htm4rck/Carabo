import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth.service';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Add both Bearer token (for tordo auth) and X-Api-Key (for Carabo tenant auth)
  const apiKey = localStorage.getItem('carabo_api_key');
  let headers = req.headers;

  if (token) {
    headers = headers.set('Authorization', `Bearer ${token}`);
  }
  if (apiKey) {
    headers = headers.set('X-Api-Key', apiKey);
  }

  return next(req.clone({ headers }));
};
