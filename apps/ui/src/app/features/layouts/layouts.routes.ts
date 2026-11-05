import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { LAYOUT_UI_CONFIG } from '@tordo-io/layout-ui/config';
import { AuthService } from '../../core/session/auth.service';
import { environment } from '../../../environments/environment';

export const LAYOUT_ROUTES: Routes = [
  {
    path: '',
    providers: [
      {
        provide: LAYOUT_UI_CONFIG,
        useFactory: () => {
          const auth = inject(AuthService);
          return {
            product: 'carabo',
            apiUrl: environment.authUrl.replace(/\/$/, '') + '/layout',
            templatesRoute: '/formats',
            businessCode: () => auth.session()?.tenantId,
            headers: () => {
              const token = auth.accessToken();
              return token ? { Authorization: 'Bearer ' + token } : {};
            },
          };
        },
      },
    ],
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('@tordo-io/layout-ui').then((m) => m.TemplateListComponent),
      },
      {
        path: 'new',
        loadComponent: () => import('@tordo-io/layout-ui').then((m) => m.TemplateEditorComponent),
      },
      {
        path: ':id/edit',
        loadComponent: () => import('@tordo-io/layout-ui').then((m) => m.TemplateEditorComponent),
      },
    ],
  },
];
