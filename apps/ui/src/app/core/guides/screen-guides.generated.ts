// Generated from Tordo.Platform/docs/manuales by scripts/generate-screen-guides.py.
export const SCREEN_GUIDES: readonly { id: string; title: string; routes: readonly string[] }[] = [
  {
    id: 'carabo-configuracion',
    title: 'Acceso, configuración e integraciones',
    routes: ['/', '/config', '/integrations'],
  },
  {
    id: 'carabo-documentos',
    title: 'Consultar y emitir documentos',
    routes: ['/documents', '/emit'],
  },
  {
    id: 'carabo-estado-fiscal',
    title: 'Revisar rechazos e incertidumbre',
    routes: ['/fiscal-status'],
  },
  {
    id: 'carabo-lotes',
    title: 'Lotes, bajas y guías',
    routes: ['/batch/summaries', '/batch/voided', '/dispatch-guides'],
  },
  {
    id: 'carabo-control',
    title: 'Control de operación y reportes',
    routes: ['/operations', '/reports', '/formats', '/formats/new', '/formats/:id/edit'],
  },
];
