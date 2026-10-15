import { Component, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CaraboApiService, DocumentSummary } from '../../services/carabo-api.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  template: `
    <div class="dashboard-head">
      <div>
        <h1>{{ greeting }}, revisa tu operación fiscal</h1>
        <p>Empresa {{ auth.session()?.tenantId }} · estado de la operación disponible</p>
      </div>
      <a routerLink="/emit" class="next-action"><span>Emitir comprobante</span><i class="ri-arrow-right-line"></i></a>
    </div>

    @if (loading()) {
      <section class="loading-panel" aria-live="polite">
        <div class="loading-line"></div><div class="loading-line short"></div><span>Sincronizando el estado fiscal</span>
      </section>
    } @else if (loadError()) {
      <section class="attention-panel">
        <i class="ri-wifi-off-line"></i><div><b>No pudimos actualizar la operación</b><span>Conservamos la navegación disponible. Revisa la conexión con la API.</span></div>
        <button type="button" (click)="loadStats()">Reintentar</button>
      </section>
    } @else {
      <section class="signal-board" aria-label="Ruta documental reciente">
        <div class="signal-summary">
          <h2>Ruta documental</h2>
          <strong>{{ totalDocuments | number }}</strong>
          <small>documentos registrados</small>
        </div>
        <div class="fiscal-flow">
          <div class="flow-step complete"><i class="ri-file-add-line"></i><b>{{ sampleCount }}</b><span>Revisados</span></div>
          <div class="flow-link complete"></div>
          <div class="flow-step" [class.complete]="signedCount > 0"><i class="ri-quill-pen-line"></i><b>{{ signedCount }}</b><span>Firmados</span></div>
          <div class="flow-link" [class.complete]="acceptedCount > 0"></div>
          <div class="flow-step" [class.complete]="acceptedCount > 0"><i class="ri-government-line"></i><b>{{ acceptedCount }}</b><span>Aceptados</span></div>
          <div class="flow-link" [class.attention]="attentionCount > 0"></div>
          <div class="flow-step" [class.attention]="attentionCount > 0"><i class="ri-radar-line"></i><b>{{ attentionCount }}</b><span>Por atender</span></div>
        </div>
      </section>

      <div class="metric-grid">
        <a routerLink="/documents" class="metric-card"><span>Aceptados en muestra</span><strong>{{ acceptedCount }}</strong><small>de {{ sampleCount }} documentos recientes</small></a>
        <a routerLink="/documents" class="metric-card"><span>En proceso</span><strong>{{ pendingCount }}</strong><small>requieren esperar respuesta</small></a>
        <a routerLink="/documents" class="metric-card" [class.needs-action]="attentionCount > 0"><span>Requieren acción</span><strong>{{ attentionCount }}</strong><small>{{ attentionCount ? 'abre el detalle para resolver' : 'sin incidencias en la muestra' }}</small></a>
        <a routerLink="/reports" class="metric-card"><span>Tipos documentales</span><strong>{{ activeTypeCount }}</strong><small>con actividad reciente</small></a>
      </div>

      <div class="work-grid">
        <section class="recent-panel">
          <div class="panel-head"><div><h2>Documentos recientes</h2><p>Últimos movimientos disponibles</p></div><a routerLink="/documents">Ver todos <i class="ri-arrow-right-line"></i></a></div>
          @if (recentDocs.length === 0) {
            <div class="empty-state"><i class="ri-file-list-3-line"></i><b>Aún no hay documentos</b><span>La siguiente acción es emitir el primer comprobante.</span><a routerLink="/emit">Comenzar emisión</a></div>
          } @else {
            <div class="document-table">
              <div class="document-row table-head"><span>Documento</span><span>Adquirente</span><span>Estado</span><span>Total</span></div>
              @for (doc of recentDocs.slice(0, 6); track doc.id) {
                <a class="document-row" [routerLink]="['/documents']">
                  <span class="doc-id"><b>{{ doc.fullNumber }}</b><small>{{ doc.docTypeName }}</small></span>
                  <span class="receiver">{{ doc.receiverName || 'Sin razón social' }}</span>
                  <span><i class="status-dot" [attr.data-status]="doc.status"></i>{{ statusLabel(doc.status) }}</span>
                  <strong>{{ doc.currency }} {{ doc.total | number:'1.2-2' }}</strong>
                </a>
              }
            </div>
          }
        </section>

        <aside class="next-panel">
          <span class="panel-icon"><i class="ri-route-line"></i></span>
          <h2>Siguiente acción</h2>
          @if (attentionCount > 0) {
            <b>Resolver {{ attentionCount }} {{ attentionCount === 1 ? 'incidencia' : 'incidencias' }}</b>
            <p>Revisa primero documentos rechazados o con error antes de continuar emitiendo.</p>
            <a routerLink="/documents">Revisar incidencias <i class="ri-arrow-right-line"></i></a>
          } @else {
            <b>Operación lista para emitir</b>
            <p>No hay incidencias visibles en la muestra reciente. Puedes continuar con un nuevo comprobante.</p>
            <a routerLink="/emit">Emitir comprobante <i class="ri-arrow-right-line"></i></a>
          }
          <div class="role-note"><i class="ri-shield-user-line"></i><span>Acceso actual<b>{{ roleLabel }}</b></span></div>
        </aside>
      </div>
    }
  `,
  styles: [`
    .dashboard-head{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;margin:6px 0 24px}.today{display:block;color:var(--accentStrong);font-size:11px;font-weight:900;letter-spacing:.11em;text-transform:uppercase;margin-bottom:8px}.dashboard-head h1{margin:0;color:var(--forestColor);font-size:30px;letter-spacing:-.035em}.dashboard-head p{margin:7px 0 0;color:var(--mutedColor);font-size:13px}.next-action{display:flex;align-items:center;gap:24px;padding:13px 16px 13px 19px;border-radius:7px;background:var(--signalColor);color:var(--forestColor);font-weight:900;box-shadow:0 8px 20px rgba(82,120,82,.16)}.next-action:hover{color:var(--forestColor);transform:translateY(-1px);box-shadow:0 12px 26px rgba(82,120,82,.22)}.next-action i{font-size:19px;transition:transform .18s}.next-action:hover i{transform:translateX(4px)}
    .signal-board{display:grid;grid-template-columns:220px 1fr;gap:30px;align-items:center;padding:25px;background:var(--forestColor);color:#fff;border-radius:10px;box-shadow:0 16px 38px rgba(23,51,23,.15)}.signal-summary{border-right:1px solid rgba(255,255,255,.14)}.signal-summary h2{margin:0;color:#8cbd8c;font-size:12px;font-weight:800}.signal-summary strong{display:block;margin:8px 0 2px;font-size:37px;font-variant-numeric:tabular-nums}.signal-summary small{color:#b8cbbb}.fiscal-flow{display:flex;align-items:center}.flow-step{width:92px;text-align:center;color:#829485}.flow-step i{display:grid;place-items:center;width:34px;height:34px;margin:0 auto 8px;border:1px solid #5d725f;border-radius:50%;font-size:17px}.flow-step b,.flow-step span{display:block}.flow-step b{color:#d7e4d9;font-size:17px}.flow-step span{font-size:10px;margin-top:2px}.flow-step.complete{color:#fff}.flow-step.complete i{color:#173317;background:#80ef80;border-color:#80ef80}.flow-step.attention i{color:#fff;background:#9a6230;border-color:#b9793f}.flow-link{height:2px;flex:1;background:#4c624f;margin-top:-27px}.flow-link.complete{background:#80ef80}.flow-link.attention{background:#b9793f}
    .metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:16px 0}.metric-card{padding:17px 18px;background:#fff;border:1px solid var(--borderColor);border-radius:8px;color:var(--forestColor);transition:transform .18s,border-color .18s,box-shadow .18s}.metric-card:hover{color:var(--forestColor);transform:translateY(-2px);border-color:#8cbd8c;box-shadow:0 10px 24px rgba(23,51,23,.08)}.metric-card>span,.metric-card small{display:block}.metric-card>span{color:#627565;font-size:12px;font-weight:700}.metric-card strong{display:block;margin:8px 0 4px;font-size:26px;font-variant-numeric:tabular-nums}.metric-card small{color:#89968b;font-size:10px}.metric-card.needs-action{border-color:#d4aa85;background:#fffaf6}.metric-card.needs-action strong{color:#8d4d24}
    .work-grid{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:16px}.recent-panel,.next-panel,.loading-panel,.attention-panel{background:#fff;border:1px solid var(--borderColor);border-radius:9px}.recent-panel{overflow:hidden}.panel-head{display:flex;justify-content:space-between;align-items:center;padding:20px 21px;border-bottom:1px solid var(--borderColor)}.panel-head h2,.next-panel h2{margin:0;color:var(--forestColor);font-size:16px}.panel-head p{margin:4px 0 0;color:var(--mutedColor);font-size:11px}.panel-head a{display:flex;align-items:center;gap:6px;color:var(--accentStrong);font-size:12px;font-weight:800}.document-row{display:grid;grid-template-columns:1.1fr 1.4fr .8fr .8fr;gap:12px;align-items:center;padding:13px 21px;border-bottom:1px solid #e8eee9;color:#394b3c;font-size:12px}.document-row:not(.table-head):hover{color:#173317;background:#f6faf6}.document-row>strong{text-align:right;font-variant-numeric:tabular-nums}.table-head{color:#7d8d80;background:#fafcfa;font-size:9px;font-weight:900;letter-spacing:.09em;text-transform:uppercase}.doc-id b,.doc-id small{display:block}.doc-id b{color:#173317}.doc-id small{margin-top:3px;color:#859387;font-size:9px}.status-dot{display:inline-block;width:7px;height:7px;margin-right:6px;border-radius:50%;background:#8cbd8c}.status-dot[data-status=REJECTED],.status-dot[data-status=ERROR]{background:#b55b50}.status-dot[data-status=PENDING],.status-dot[data-status=VALIDATED],.status-dot[data-status=SIGNED],.status-dot[data-status=SENT]{background:#c88b43}
    .next-panel{padding:22px;display:flex;flex-direction:column}.panel-icon{display:grid;place-items:center;width:40px;height:40px;border-radius:7px;background:#e8f5e9;color:#426845;font-size:21px}.next-panel h2{margin-top:18px}.next-panel>b{margin:22px 0 7px;color:#173317;font-size:18px;line-height:1.25}.next-panel p{color:#6f7f71;font-size:12px;line-height:1.6}.next-panel>a{display:flex;justify-content:space-between;align-items:center;margin-top:12px;padding:12px 14px;border-radius:6px;background:#80ef80;color:#173317;font-size:12px;font-weight:900}.role-note{display:flex;align-items:center;gap:10px;margin-top:auto;padding-top:24px;color:#708172;font-size:11px}.role-note i{font-size:18px;color:#527852}.role-note span,.role-note b{display:block}.role-note b{margin-top:2px;color:#173317;text-transform:capitalize}.empty-state{display:grid;justify-items:center;padding:50px 20px;color:#7c8c7f;text-align:center}.empty-state>i{font-size:31px;color:#8cbd8c}.empty-state b{margin:12px 0 5px;color:#173317}.empty-state a{margin-top:17px;color:#426845;font-weight:900}.loading-panel{padding:35px}.loading-line{height:14px;background:#e5ece6;border-radius:4px;margin-bottom:12px;animation:pulse 1.3s ease-in-out infinite}.loading-line.short{width:55%}.loading-panel span{color:#768a76;font-size:12px}.attention-panel{display:flex;align-items:center;gap:14px;padding:20px}.attention-panel>i{font-size:24px;color:#9b5b2e}.attention-panel div{flex:1}.attention-panel b,.attention-panel span{display:block}.attention-panel span{margin-top:4px;color:#758278;font-size:12px}.attention-panel button{border:1px solid #8cbd8c;background:#fff;padding:9px 12px;border-radius:6px;color:#173317;font-weight:800;cursor:pointer}@keyframes pulse{50%{opacity:.45}}
    @media(max-width:1050px){.metric-grid{grid-template-columns:repeat(2,1fr)}.work-grid{grid-template-columns:1fr}.next-panel{min-height:260px}.signal-board{grid-template-columns:1fr}.signal-summary{border:0;border-bottom:1px solid rgba(255,255,255,.14);padding-bottom:18px}}
    @media(max-width:680px){.dashboard-head{align-items:flex-start;flex-direction:column}.next-action{width:100%;justify-content:space-between}.metric-grid{grid-template-columns:1fr 1fr}.fiscal-flow{overflow-x:auto;padding-bottom:5px}.flow-step{min-width:72px}.flow-link{min-width:30px}.document-row{grid-template-columns:1fr .8fr}.document-row .receiver,.table-head span:nth-child(2){display:none}.dashboard-head h1{font-size:25px}}
  `]
})
export class DashboardComponent implements OnInit {
  readonly loading = signal(true);
  readonly loadError = signal(false);
  recentDocs: DocumentSummary[] = [];
  totalDocuments = 0;
  acceptedCount = 0;
  pendingCount = 0;
  attentionCount = 0;
  signedCount = 0;
  activeTypeCount = 0;

  constructor(private readonly api: CaraboApiService, public readonly auth: AuthService) {}

  ngOnInit(): void { this.loadStats(); }
  get sampleCount(): number { return this.recentDocs.length; }
  get greeting(): string {
    const hour = new Date().getHours();
    return hour < 12 ? 'Buenos días' : hour < 19 ? 'Buenas tardes' : 'Buenas noches';
  }
  get roleLabel(): string { return (this.auth.session()?.roles?.[0] ?? 'Usuario').replace('_Carabo', '').replace('_PLATFORM', '').toLowerCase(); }

  loadStats(): void {
    this.loading.set(true); this.loadError.set(false);
    this.api.queryDocuments({ limit: 10, offset: 0 }).subscribe({
      next: response => {
        this.recentDocs = response.items;
        this.totalDocuments = response.total;
        this.acceptedCount = response.items.filter(doc => doc.status === 'ACCEPTED').length;
        this.pendingCount = response.items.filter(doc => ['PENDING', 'VALIDATED', 'SIGNED', 'SENT'].includes(doc.status)).length;
        this.attentionCount = response.items.filter(doc => ['REJECTED', 'ERROR'].includes(doc.status)).length;
        this.signedCount = response.items.filter(doc => ['SIGNED', 'SENT', 'ACCEPTED', 'REJECTED'].includes(doc.status)).length;
        this.activeTypeCount = new Set(response.items.map(doc => doc.docType)).size;
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.loadError.set(true); },
    });
  }

  statusLabel(status: string): string {
    return ({ ACCEPTED: 'Aceptado', REJECTED: 'Rechazado', ERROR: 'Error', PENDING: 'Pendiente', VALIDATED: 'Validado', SIGNED: 'Firmado', SENT: 'Enviado' } as Record<string,string>)[status] ?? status;
  }
}
