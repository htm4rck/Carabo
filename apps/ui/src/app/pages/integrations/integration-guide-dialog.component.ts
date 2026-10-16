import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { CaraboApiService, IntegrationValidationReport, ProducerIntegration } from '../../services/carabo-api.service';
import { DOCUMENT_TYPE_LABELS, IntegrationDocumentType, exampleExternalId, integrationExample } from './integration-examples';
import { CodeBlockComponent } from './code-block.component';

export interface IntegrationGuideData {
  producer: ProducerIntegration | null;
  apiBaseUrl: string;
  canAdminister: boolean;
}

@Component({
  selector: 'app-integration-guide-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatDialogModule, MatIconModule, MatSnackBarModule, CodeBlockComponent],
  template: `
    <div class="dialog-shell">
      <header class="dialog-header">
        <div><h2>Conectar {{ data.producer?.name || 'una aplicación productora' }}</h2><p>Contrato ejecutable para autenticar, publicar, validar y conciliar sin credenciales humanas.</p></div>
        <button type="button" mat-icon-button mat-dialog-close aria-label="Cerrar documentación"><mat-icon>close</mat-icon></button>
      </header>

      <div class="dialog-layout">
        <nav class="doc-nav" aria-label="Secciones de integración">
          <button type="button" [class.active]="section==='credential'" (click)="section='credential'"><mat-icon>key</mat-icon><span><strong>Credencial y URL</strong><small>Registrar el secreto</small></span></button>
          <button type="button" [class.active]="section==='transport'" (click)="section='transport'"><mat-icon>swap_horiz</mat-icon><span><strong>Canal de envío</strong><small>HTTP o RabbitMQ</small></span></button>
          <button type="button" [class.active]="section==='payload'" (click)="section='payload'"><mat-icon>data_object</mat-icon><span><strong>Cuerpos completos</strong><small>Factura, boleta y notas</small></span></button>
          <button type="button" [class.active]="section==='validator'" (click)="section='validator'"><mat-icon>rule</mat-icon><span><strong>Comparar documento</strong><small>Ruta, valor y corrección</small></span></button>
        </nav>

        <main class="doc-content">
          @switch (section) {
            @case ('credential') {
              <section>
                <h3>Registra la credencial en el backend productor</h3>
                <p>Carabo revela la API key sólo cuando un Admin/Root crea o rota el productor. Guárdala como secreto del servidor; nunca en JavaScript, repositorios, logs ni aplicaciones móviles.</p>
                <div class="contract-grid">
                  <div><span>Base URL</span><code>{{ data.apiBaseUrl }}</code><button type="button" mat-icon-button aria-label="Copiar Base URL" (click)="copy(data.apiBaseUrl,'Base URL copiada')"><mat-icon>content_copy</mat-icon></button></div>
                  <div><span>Header</span><code>X-Api-Key: &#60;CARABO_API_KEY&#62;</code><button type="button" mat-icon-button aria-label="Copiar header" (click)="copy('X-Api-Key: <CARABO_API_KEY>','Header copiado')"><mat-icon>content_copy</mat-icon></button></div>
                  <div><span>Productor</span><code>{{ data.producer?.code || 'selecciona un productor' }}</code></div>
                  <div><span>Scopes</span><code>{{ data.producer?.scopes?.join(', ') || 'DOCUMENT_INGEST, DOCUMENT_READ' }}</code></div>
                </div>
                <h4>1. Registra el secreto</h4>
                <app-code-block [code]="secretExample" label="Copiar variable" (copyCode)="copy(secretExample,'Configuración copiada')"></app-code-block>
                <h4>2. Verifica que nunca llegue al frontend</h4>
                <ul class="check-list"><li><mat-icon>check</mat-icon>El backend lee <code>CARABO_API_KEY</code> desde su secret store.</li><li><mat-icon>check</mat-icon>La clave sólo viaja servidor a servidor mediante HTTPS.</li><li><mat-icon>check</mat-icon>Cada productor tiene una clave distinta y puede revocarse aisladamente.</li></ul>
                @if (!data.producer) { <div class="notice"><mat-icon>info</mat-icon><p>Selecciona <strong>Ver integración</strong> en un productor para personalizar esta guía. {{ data.canAdminister ? 'Si aún no existe, créalo desde la pantalla anterior.' : 'Un Admin/Root debe crearlo y custodiar la credencial.' }}</p></div> }
              </section>
            }
            @case ('transport') {
              <section>
                <h3>Elige el canal sin duplicar responsabilidades</h3>
                <div class="transport-tabs" role="tablist" aria-label="Canal de transporte">
                  <button type="button" role="tab" [attr.aria-selected]="transport==='http'" [class.active]="transport==='http'" (click)="transport='http'"><mat-icon>https</mat-icon><span><strong>HTTP API</strong><small>Disponible para productores externos</small></span></button>
                  <button type="button" role="tab" [attr.aria-selected]="transport==='rabbit'" [class.active]="transport==='rabbit'" (click)="transport='rabbit'"><mat-icon>hub</mat-icon><span><strong>RabbitMQ directo</strong><small>Privado; no habilitado para clientes</small></span></button>
                </div>
                @if (transport==='http') {
                  <div class="status-line ready"><mat-icon>check_circle</mat-icon><span><strong>Canal soportado.</strong> Carabo confirma el mensaje en RabbitMQ antes de responder <code>202</code>.</span></div>
                  <dl class="endpoint-list"><div><dt>Publicar</dt><dd>POST <code>{{ data.apiBaseUrl }}/documents/ingest</code></dd></div><div><dt>Validar sin emitir</dt><dd>POST <code>{{ data.apiBaseUrl }}/documents/validate</code></dd></div><div><dt>Consultar</dt><dd>GET <code>{{ data.apiBaseUrl }}/documents/external/{{ externalId }}</code></dd></div></dl>
                  <app-code-block [code]="curlExample" label="Copiar cURL completo" (copyCode)="copy(curlExample,'cURL copiado')"></app-code-block>
                  <p class="caption"><code>202</code> confirma la publicación interna; no significa que SUNAT ya aceptó el comprobante.</p>
                } @else {
                  <div class="status-line blocked"><mat-icon>lock</mat-icon><span><strong>No expuesto a productores externos.</strong> Una API key de Carabo no autentica AMQP y no debe reutilizarse como contraseña del broker.</span></div>
                  <p>El consumidor existente usa AMQP 0-9-1 dentro de la red Tordo. Para abrirlo a un productor se necesita primero una identidad RabbitMQ dedicada, TLS, vhost por entorno, permisos mínimos y límites de publicación. Hasta entonces usa HTTP, que ya entrega al mismo pipeline.</p>
                  <dl class="endpoint-list"><div><dt>Exchange interno</dt><dd><code>carabo.exchange</code> · direct</dd></div><div><dt>Routing key</dt><dd><code>document.emit</code></dd></div><div><dt>Cola consumidora</dt><dd><code>carabo.document.emit</code></dd></div><div><dt>Autenticación futura</dt><dd>Usuario AMQP dedicado + TLS; no <code>X-Api-Key</code></dd></div></dl>
                  <div class="notice"><mat-icon>code</mat-icon><p>No se entregará una librería propietaria: el contrato es AMQP 0-9-1 y se usarían clientes oficiales como RabbitMQ Java Client, amqplib o Pika. La habilitación permanece bloqueada hasta implementar aislamiento y aprovisionamiento por productor.</p></div>
                }
              </section>
            }
            @case ('payload') {
              <section>
                <div class="content-heading"><div><h3>Cuerpo completo listo para copiar</h3><p>Cambia los datos de ejemplo; conserva tipos, nombres y estructura.</p></div><label>Tipo de documento<select [(ngModel)]="docType" (ngModelChange)="changeType()"><option value="01">Factura · 01</option><option value="03">Boleta · 03</option><option value="07">Nota de crédito · 07</option><option value="08">Nota de débito · 08</option></select></label></div>
                <div class="payload-meta"><span><strong>Header idempotente</strong><code>{{ externalId }}</code></span><span><strong>Emisor</strong><small>No se envía: Carabo lo deriva de la empresa activa.</small></span><span><strong>Importes calculados</strong><small>Carabo calcula base, IGV y total desde las líneas.</small></span></div>
                <app-code-block [code]="referenceText" label="Copiar JSON" (copyCode)="copy(referenceText,'JSON copiado')"></app-code-block>
                <h4>Solicitud completa</h4>
                <app-code-block [code]="curlExample" label="Copiar cURL" (copyCode)="copy(curlExample,'cURL copiado')"></app-code-block>
              </section>
            }
            @case ('validator') {
              <section>
                <div class="content-heading"><div><h3>Compara tu JSON antes de emitir</h3><p>La validación no reserva correlativo ni envía a SUNAT. Cada hallazgo indica la ruta exacta, lo recibido y cómo corregirlo.</p></div><button type="button" mat-stroked-button (click)="loadReference()"><mat-icon>restart_alt</mat-icon>Cargar ejemplo correcto</button></div>
                <label class="idempotency-field">Idempotency-Key<input [(ngModel)]="validationKey" maxlength="100"></label>
                <div class="compare-grid">
                  <label class="json-editor">Tu documento<textarea [(ngModel)]="payloadText" spellcheck="false" aria-label="JSON a validar"></textarea></label>
                  <div class="reference-pane"><div><strong>Documento correcto · {{ documentLabel }}</strong><button type="button" mat-button (click)="copy(referenceText,'Ejemplo copiado')">Copiar</button></div><pre><code>{{ referenceText }}</code></pre></div>
                </div>
                <div class="validator-actions"><button type="button" mat-raised-button color="primary" (click)="validate()" [disabled]="validating"><mat-icon>{{ validating ? 'progress_activity' : 'rule' }}</mat-icon>{{ validating ? 'Comparando…' : 'Comparar documento' }}</button><span>Endpoint: <code>POST /documents/validate</code></span></div>
                @if (parseError) { <div class="status-line blocked" role="alert"><mat-icon>error</mat-icon><span><strong>JSON inválido.</strong> {{ parseError }}</span></div> }
                @if (report) {
                  <div class="validation-summary" [class.valid]="report.valid"><mat-icon>{{ report.valid ? 'task_alt' : 'error' }}</mat-icon><div><strong>{{ report.valid ? 'Documento estructuralmente válido' : errorCount+' errores por corregir' }}</strong><span>{{ warningCount }} advertencias · ninguna emisión fue realizada</span></div></div>
                  @if (report.issues.length) { <div class="issue-list">@for (issue of report.issues; track issue.path+issue.code) { <article [class.warning]="issue.severity==='WARNING'"><div class="issue-path"><span>{{ issue.severity==='ERROR' ? 'Error' : 'Advertencia' }}</span><code>{{ issue.path }}</code></div><div><strong>{{ issue.message }}</strong><p>{{ issue.suggestion }}</p><dl><div><dt>Recibido</dt><dd><code>{{ issue.received ?? 'ausente' }}</code></dd></div><div><dt>Esperado</dt><dd><code>{{ issue.expected }}</code></dd></div></dl></div></article> }</div> }
                }
              </section>
            }
          }
        </main>
      </div>
    </div>
  `,
  styles: [`
    :host{display:block;color:#173317}.dialog-shell{width:min(1180px,94vw);height:min(820px,90vh);display:flex;flex-direction:column;background:#f3f7f3}.dialog-header{display:flex;justify-content:space-between;align-items:flex-start;gap:20px;padding:22px 24px;background:#173317;color:#fff}.dialog-header h2{margin:0 0 5px;font:700 25px/1.15 Lato,sans-serif;letter-spacing:-.025em}.dialog-header p{margin:0;color:#d8e6d8;line-height:1.5}.dialog-header button{color:#fff}.dialog-layout{min-height:0;display:grid;grid-template-columns:240px 1fr;flex:1}.doc-nav{padding:16px;background:#fff;border-inline-end:1px solid #d8e3d9;display:flex;flex-direction:column;gap:5px}.doc-nav button{display:flex;align-items:center;gap:11px;width:100%;border:0;background:transparent;color:#527052;text-align:start;padding:11px;border-radius:7px;cursor:pointer;font-family:Lato,sans-serif}.doc-nav button:hover{background:#f1f6f1}.doc-nav button.active{background:#80ef80;color:#173317}.doc-nav span,.doc-nav strong,.doc-nav small{display:block}.doc-nav strong{font-size:13px}.doc-nav small{margin-top:2px;font-size:10px;color:inherit}.doc-content{min-width:0;overflow:auto;padding:25px}.doc-content section{max-width:850px}.doc-content h3{margin:0 0 8px;font-size:21px;letter-spacing:-.02em}.doc-content h4{margin:24px 0 8px;font-size:14px}.doc-content p{color:#687a6b;line-height:1.65}.doc-content code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}.contract-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:20px 0}.contract-grid>div{min-width:0;display:grid;grid-template-columns:110px 1fr auto;align-items:center;gap:8px;padding:10px 12px;background:#fff;border:1px solid #d8e3d9;border-radius:7px}.contract-grid span{font-size:11px;font-weight:800;text-transform:uppercase;letter-spacing:.04em;color:#687a6b}.contract-grid code{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.check-list{list-style:none;padding:0;display:grid;gap:8px}.check-list li{display:flex;align-items:center;gap:8px;color:#526554}.check-list mat-icon{color:#527852;font-size:19px;width:19px;height:19px}.notice,.status-line{display:flex;align-items:flex-start;gap:10px;padding:13px 15px;border-radius:7px;margin:16px 0}.notice{background:#eef6ef;color:#3f6244}.notice p{margin:0;color:inherit}.status-line.ready{background:#e8f8e9;color:#285f31}.status-line.blocked{background:#fff3e6;color:#7b4b1f}.status-line mat-icon{flex:0 0 auto}.status-line span{line-height:1.5}.transport-tabs{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:20px 0}.transport-tabs button{display:flex;align-items:center;gap:11px;padding:15px;border:1px solid #b8c9ba;border-radius:8px;background:#fff;text-align:start;color:#527052;cursor:pointer}.transport-tabs button.active{border-color:#527852;background:#eaf8eb;color:#173317;box-shadow:0 7px 20px rgba(23,51,23,.07)}.transport-tabs span,.transport-tabs strong,.transport-tabs small{display:block}.transport-tabs small{margin-top:3px}.endpoint-list{display:grid;margin:18px 0;border-top:1px solid #d8e3d9}.endpoint-list>div{display:grid;grid-template-columns:155px 1fr;gap:12px;padding:11px 0;border-bottom:1px solid #d8e3d9}.endpoint-list dt{font-size:12px;font-weight:800}.endpoint-list dd{margin:0;color:#526554}.caption{font-size:12px}.content-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.content-heading p{margin:0}.content-heading label,.idempotency-field{display:flex;flex-direction:column;gap:6px;font-size:11px;font-weight:800;color:#526554}.content-heading select,.idempotency-field input{min-width:190px;height:42px;border:1px solid #b8c9ba;border-radius:7px;background:#fff;color:#173317;padding:0 11px;font:inherit}.payload-meta{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin:18px 0}.payload-meta>span{padding:10px;background:#fff;border:1px solid #d8e3d9;border-radius:7px}.payload-meta strong,.payload-meta small,.payload-meta code{display:block}.payload-meta strong{font-size:11px;margin-bottom:4px}.payload-meta small{color:#687a6b;line-height:1.4}.payload-meta code{font-size:10px;overflow-wrap:anywhere}.idempotency-field{margin:18px 0}.idempotency-field input{width:100%;font-family:ui-monospace,SFMono-Regular,Consolas,monospace}.compare-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.json-editor{display:flex;flex-direction:column;gap:7px;font-size:12px;font-weight:800}.json-editor textarea,.reference-pane pre{height:350px;margin:0;padding:14px;border:1px solid #b8c9ba;border-radius:7px;background:#fff;color:#173317;font:12px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;overflow:auto;white-space:pre}.json-editor textarea{resize:vertical;outline:none}.json-editor textarea:focus{border-color:#527852;box-shadow:0 0 0 3px rgba(128,239,128,.28)}.reference-pane{min-width:0}.reference-pane>div{height:24px;display:flex;justify-content:space-between;align-items:center;font-size:12px}.reference-pane pre{background:#173317;color:#edf7ed;border-color:#173317}.validator-actions{display:flex;align-items:center;gap:14px;margin:16px 0}.validator-actions>span{font-size:11px;color:#687a6b}.validation-summary{display:flex;align-items:center;gap:11px;padding:14px;background:#fbeceb;color:#8b302a;border-radius:7px}.validation-summary.valid{background:#e8f8e9;color:#285f31}.validation-summary strong,.validation-summary span{display:block}.validation-summary span{font-size:11px;margin-top:2px}.issue-list{display:grid;gap:8px;margin-top:12px}.issue-list article{display:grid;grid-template-columns:180px 1fr;gap:14px;padding:13px;background:#fff;border:1px solid #e5c4c1;border-radius:7px}.issue-list article.warning{border-color:#ead2b8}.issue-path span,.issue-path code{display:block}.issue-path span{font-size:10px;font-weight:900;text-transform:uppercase;color:#9d332c}.issue-path code{margin-top:5px;font-size:11px;overflow-wrap:anywhere}.issue-list article.warning .issue-path span{color:#8a5729}.issue-list strong{font-size:13px}.issue-list p{margin:3px 0 8px;font-size:12px;line-height:1.45}.issue-list dl{display:flex;gap:18px;margin:0}.issue-list dl div{min-width:0}.issue-list dt{font-size:9px;font-weight:900;text-transform:uppercase;color:#687a6b}.issue-list dd{margin:2px 0 0;overflow-wrap:anywhere;font-size:11px}@media(max-width:800px){.dialog-shell{width:96vw;height:94vh}.dialog-layout{grid-template-columns:1fr}.doc-nav{display:grid;grid-template-columns:1fr 1fr;padding:10px;border-inline-end:0;border-bottom:1px solid #d8e3d9}.doc-nav button{padding:9px}.doc-content{padding:18px}.contract-grid,.transport-tabs,.compare-grid,.payload-meta{grid-template-columns:1fr}.content-heading{flex-direction:column}.content-heading select{width:100%}.issue-list article{grid-template-columns:1fr}.dialog-header{padding:18px}.dialog-header h2{font-size:21px}}@media(max-width:480px){.doc-nav small{display:none}.doc-nav button{align-items:center}.doc-content{padding:14px}.contract-grid>div{grid-template-columns:90px 1fr}.contract-grid button{display:none}.validator-actions{align-items:stretch;flex-direction:column}.validator-actions button{width:100%}}
  `]
})
export class IntegrationGuideDialogComponent {
  section: 'credential' | 'transport' | 'payload' | 'validator' = 'credential';
  transport: 'http' | 'rabbit' = 'http';
  docType: IntegrationDocumentType = '01';
  payloadText = '';
  validationKey = '';
  validating = false;
  parseError = '';
  report: IntegrationValidationReport | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: IntegrationGuideData,
    private api: CaraboApiService,
    private snack: MatSnackBar
  ) { this.loadReference(); }

  get externalId(): string { return exampleExternalId(this.docType); }
  get documentLabel(): string { return DOCUMENT_TYPE_LABELS[this.docType]; }
  get referenceText(): string { return JSON.stringify(integrationExample(this.docType), null, 2); }
  get secretExample(): string { return `# Secret store / variable del backend productor\nCARABO_API_KEY=<credencial revelada por Carabo>\nCARABO_BASE_URL=${this.data.apiBaseUrl}`; }
  get curlExample(): string {
    const body = this.referenceText.replaceAll("'", "'\\''");
    return `curl --request POST '${this.data.apiBaseUrl}/documents/ingest' \\\n  --header 'X-Api-Key: <CARABO_API_KEY>' \\\n  --header 'Idempotency-Key: ${this.externalId}' \\\n  --header 'Content-Type: application/json' \\\n  --data '${body}'`;
  }
  get errorCount(): number { return this.report?.issues.filter(issue => issue.severity === 'ERROR').length || 0; }
  get warningCount(): number { return this.report?.issues.filter(issue => issue.severity === 'WARNING').length || 0; }

  changeType(): void { this.report = null; this.parseError = ''; this.loadReference(); }
  loadReference(): void { this.payloadText = this.referenceText; this.validationKey = this.externalId; this.report = null; this.parseError = ''; }
  validate(): void {
    this.parseError = ''; this.report = null;
    let payload: unknown;
    try { payload = JSON.parse(this.payloadText); }
    catch (error) { this.parseError = error instanceof Error ? error.message : 'Revisa comas, llaves y comillas.'; return; }
    this.validating = true;
    this.api.validateIntegrationPayload(payload, this.validationKey).pipe(finalize(() => this.validating = false)).subscribe({
      next: report => this.report = report,
      error: error => {
        const message = error?.status === 403 ? 'Tu rol o productor no tiene DOCUMENT_INGEST.' : 'No fue posible validar. Revisa la conexión y vuelve a intentar.';
        this.snack.open(message, 'Cerrar', { duration: 5000 });
      }
    });
  }
  copy(value: string, message: string): void {
    if (!navigator.clipboard) { this.snack.open('Selecciona y copia el contenido manualmente.', 'Cerrar', { duration: 4000 }); return; }
    navigator.clipboard.writeText(value).then(() => this.snack.open(message, 'Cerrar', { duration: 2200 }))
      .catch(() => this.snack.open('El navegador rechazó el portapapeles. Copia manualmente.', 'Cerrar', { duration: 4500 }));
  }
}
