import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors,
  withXhr,
  withXsrfConfiguration,
} from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { routes } from './app.routes';
import { apiKeyInterceptor } from './core/http/api-key.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withXhr(),
      withXsrfConfiguration({ cookieName: '__Host-tordo-xsrf', headerName: 'x-tordo-csrf' }),
      withInterceptors([apiKeyInterceptor]),
    ),
    provideAnimationsAsync(),
  ],
};
