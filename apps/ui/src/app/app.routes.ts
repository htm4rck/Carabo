import { Routes } from '@angular/router';
import { adminGuard, authGuard, supervisorGuard } from './core/session/guards/auth.guard';
import { pendingCredentialGuard } from './core/session/guards/pending-credential.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/pages/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/dashboard.component').then(
            (m) => m.DashboardComponent,
          ),
      },
      {
        path: 'config',
        canActivate: [authGuard, adminGuard],
        loadComponent: () =>
          import('./features/config/pages/config.component').then((m) => m.ConfigComponent),
      },
      {
        path: 'documents',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/documents/pages/documents.component').then(
            (m) => m.DocumentsComponent,
          ),
      },
      {
        path: 'emit',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/emit/pages/emit.component').then((m) => m.EmitComponent),
      },
      {
        path: 'reports',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/reports/pages/reports.component').then((m) => m.ReportsComponent),
      },
      {
        path: 'batch/summaries',
        canActivate: [authGuard, supervisorGuard],
        loadComponent: () =>
          import('./features/batch/pages/summaries.component').then((m) => m.SummariesComponent),
      },
      {
        path: 'batch/voided',
        canActivate: [authGuard, supervisorGuard],
        loadComponent: () =>
          import('./features/batch/pages/voided.component').then((m) => m.VoidedComponent),
      },
      {
        path: 'dispatch-guides',
        canActivate: [authGuard, supervisorGuard],
        loadComponent: () =>
          import('./features/dispatch-guides/pages/dispatch-guides.component').then(
            (m) => m.DispatchGuidesComponent,
          ),
      },
      {
        path: 'operations',
        canActivate: [authGuard, supervisorGuard],
        loadComponent: () =>
          import('./features/operations/pages/operations.component').then(
            (m) => m.OperationsComponent,
          ),
      },
      {
        path: 'fiscal-status',
        canActivate: [authGuard, supervisorGuard],
        loadComponent: () =>
          import('./features/fiscal-status/pages/fiscal-status.component').then(
            (m) => m.FiscalStatusComponent,
          ),
      },
      {
        path: 'integrations',
        canActivate: [authGuard, supervisorGuard],
        canDeactivate: [pendingCredentialGuard],
        loadComponent: () =>
          import('./features/integrations/pages/integrations.component').then(
            (m) => m.IntegrationsComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
