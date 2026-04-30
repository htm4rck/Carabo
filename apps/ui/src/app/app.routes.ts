import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'config', loadComponent: () => import('./pages/config/config.component').then(m => m.ConfigComponent) },
  { path: 'documents', loadComponent: () => import('./pages/documents/documents.component').then(m => m.DocumentsComponent) },
  { path: 'batch/summaries', loadComponent: () => import('./pages/batch/summaries.component').then(m => m.SummariesComponent) },
  { path: 'batch/voided', loadComponent: () => import('./pages/batch/voided.component').then(m => m.VoidedComponent) },
];
