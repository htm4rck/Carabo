// Generated from Tordo.Platform/docs/manuales by scripts/generate-screen-guides.py.
export interface OperationalManual {
  id: string;
  title: string;
  description: string;
  group: string;
  path: string;
  applications: readonly string[];
  routes: Readonly<Record<string, readonly string[]>>;
}
export const OPERATIONAL_MANUALS: readonly OperationalManual[] = [
  {
    id: 'carabo-manual-usuario',
    title: 'Carabo — manual de usuario',
    description: 'Carabo — manual de usuario',
    group: 'Procedimientos compartidos',
    applications: ['carabo'],
    routes: {},
    path: 'Manuales/Aplicaciones/Carabo/manual-usuario.md',
  },
  {
    id: 'compartido-acceso-y-soporte',
    title: 'Acceso y soporte para todas las aplicaciones',
    description: 'Acceso y soporte para todas las aplicaciones',
    group: 'Procedimientos compartidos',
    applications: [
      'amazilia',
      'cyanocitta',
      'aquila',
      'anser',
      'carabo',
      'columba',
      'corvus',
      'passer',
      'buteo',
      'tecolyt',
      'tordo-core',
      'tordo-service',
      'one',
      'reports',
      'odoo',
      'origins',
      'rnc',
      'learnex-exporter',
      'mimus',
      'leanix-reports',
      'sternia',
      'birdport',
    ],
    routes: {},
    path: 'Manuales/Compartidos/acceso-y-soporte.md',
  },
  {
    id: 'carabo-configuracion',
    title: 'Acceso, configuración e integraciones',
    description: 'Acceso, configuración e integraciones',
    group: 'Guía de esta pantalla',
    applications: ['carabo'],
    routes: {
      carabo: ['/', '/config', '/integrations'],
    },
    path: 'Manuales/Aplicaciones/Carabo/manual-usuario.md',
  },
  {
    id: 'carabo-documentos',
    title: 'Consultar y emitir documentos',
    description: 'Consultar y emitir documentos',
    group: 'Guía de esta pantalla',
    applications: ['carabo'],
    routes: {
      carabo: ['/documents', '/emit'],
    },
    path: 'Manuales/Aplicaciones/Carabo/manual-usuario.md',
  },
  {
    id: 'carabo-estado-fiscal',
    title: 'Revisar rechazos e incertidumbre',
    description: 'Revisar rechazos e incertidumbre',
    group: 'Guía de esta pantalla',
    applications: ['carabo'],
    routes: {
      carabo: ['/fiscal-status'],
    },
    path: 'Manuales/Aplicaciones/Carabo/manual-usuario.md',
  },
  {
    id: 'carabo-lotes',
    title: 'Lotes, bajas y guías',
    description: 'Lotes, bajas y guías',
    group: 'Guía de esta pantalla',
    applications: ['carabo'],
    routes: {
      carabo: ['/batch/summaries', '/batch/voided', '/dispatch-guides'],
    },
    path: 'Manuales/Aplicaciones/Carabo/manual-usuario.md',
  },
  {
    id: 'carabo-control',
    title: 'Control de operación y reportes',
    description: 'Control de operación y reportes',
    group: 'Guía de esta pantalla',
    applications: ['carabo'],
    routes: {
      carabo: ['/operations', '/reports', '/formats', '/formats/new', '/formats/:id/edit'],
    },
    path: 'Manuales/Aplicaciones/Carabo/manual-usuario.md',
  },
];
