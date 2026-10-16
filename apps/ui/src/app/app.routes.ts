import { Routes } from '@angular/router';
import { adminGuard, authGuard, supervisorGuard } from './auth/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: '', canActivate: [authGuard], loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'config', canActivate: [authGuard, adminGuard], loadComponent: () => import('./pages/config/config.component').then(m => m.ConfigComponent) },
  { path: 'documents', canActivate: [authGuard], loadComponent: () => import('./pages/documents/documents.component').then(m => m.DocumentsComponent) },
  { path: 'emit', canActivate: [authGuard], loadComponent: () => import('./pages/emit/emit.component').then(m => m.EmitComponent) },
  { path: 'reports', canActivate: [authGuard], loadComponent: () => import('./pages/reports/reports.component').then(m => m.ReportsComponent) },
  { path: 'batch/summaries', canActivate: [authGuard, supervisorGuard], loadComponent: () => import('./pages/batch/summaries.component').then(m => m.SummariesComponent) },
  { path: 'batch/voided', canActivate: [authGuard, supervisorGuard], loadComponent: () => import('./pages/batch/voided.component').then(m => m.VoidedComponent) },
  { path: 'dispatch-guides', canActivate: [authGuard, supervisorGuard], loadComponent: () => import('./pages/dispatch-guides/dispatch-guides.component').then(m => m.DispatchGuidesComponent) },
  { path: 'operations', canActivate: [authGuard, supervisorGuard], loadComponent: () => import('./pages/operations/operations.component').then(m => m.OperationsComponent) },
  { path: 'fiscal-status', canActivate: [authGuard, supervisorGuard], loadComponent: () => import('./pages/fiscal-status/fiscal-status.component').then(m => m.FiscalStatusComponent) },
  { path: 'integrations', canActivate: [authGuard, supervisorGuard], loadComponent: () => import('./pages/integrations/integrations.component').then(m => m.IntegrationsComponent) },
  { path: '**', redirectTo: '' },
];
