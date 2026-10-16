import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../auth/auth.service';
import { CaraboApiService, ProducerCredential, ProducerIntegration, ProducerScope } from '../../services/carabo-api.service';
import { environment } from '../../../environments/environment';
import { IntegrationGuideDialogComponent } from './integration-guide-dialog.component';

@Component({
  selector: 'app-integrations',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatDialogModule, MatIconModule, MatSnackBarModule],
  template: `
    <header class="page-heading">
      <div><h1>Integraciones</h1><p>Identidades independientes para ERP, ecommerce, POS y otras aplicaciones productoras.</p></div>
      @if (auth.canAdminister()) { <button type="button" mat-raised-button color="primary" (click)="creating=!creating" [disabled]="!!credential" [title]="credential ? 'Confirma que guardaste la credencial pendiente antes de continuar' : ''"><mat-icon>{{ creating ? 'close' : 'add' }}</mat-icon>{{ creating ? 'Cerrar formulario' : 'Nuevo productor' }}</button> }
    </header>

    <section class="trust-strip" aria-label="Modelo de seguridad">
      <mat-icon>key</mat-icon><p><strong>Credenciales de máquina, no usuarios.</strong> Cada productor usa su propia API key y puede revocarse sin afectar a las personas ni a otras aplicaciones.</p>
      <span>{{ activeCount }} activas · {{ degradedCount }} requieren atención · {{ revokedCount }} revocadas</span>
    </section>

    @if (credential) {
      <section class="credential-panel" aria-live="polite">
        <div><mat-icon>vpn_key</mat-icon><span><strong>Guarda ahora la credencial de {{ credential.producer.name }}</strong>Se muestra una sola vez. Consérvala en el secret store del productor.</span></div>
        <code>{{ credential.apiKey }}</code>
        <button type="button" mat-stroked-button (click)="copyCredential()"><mat-icon>content_copy</mat-icon>Copiar credencial</button>
        <label class="stored-confirmation"><input type="checkbox" [(ngModel)]="credentialStored">Confirmo que la guardé</label>
        <button type="button" mat-raised-button color="primary" [disabled]="!credentialStored" (click)="dismissCredential()">Ya la guardé</button>
      </section>
    }

    @if (creating && auth.canAdminister()) {
      <section class="editor-panel">
        <div class="section-heading"><div><h2>Registrar aplicación productora</h2><p>La identidad pertenece al sistema, no a una cuenta humana.</p></div></div>
        <form (ngSubmit)="create()" #producerForm="ngForm">
          <label>Nombre visible<input name="name" [(ngModel)]="draft.name" required maxlength="150" placeholder="ERP principal"></label>
          <label>Código técnico<input name="code" [(ngModel)]="draft.code" required minlength="3" maxlength="50" pattern="[a-z0-9][a-z0-9_-]{2,49}" placeholder="erp-principal"><small>Minúsculas, números, guion o guion bajo.</small></label>
          <label class="wide">Propósito<textarea name="description" [(ngModel)]="draft.description" maxlength="500" rows="2" placeholder="Emite facturas aprobadas desde el ERP comercial"></textarea></label>
          <fieldset class="wide"><legend>Permisos</legend>
            <label class="scope"><input type="checkbox" name="ingest" [(ngModel)]="scopeSelection.DOCUMENT_INGEST"><span><strong>Enviar documentos</strong><small>POST /documents/ingest</small></span></label>
            <label class="scope"><input type="checkbox" name="read" [(ngModel)]="scopeSelection.DOCUMENT_READ"><span><strong>Consultar resultados</strong><small>Estado, XML, CDR y PDF</small></span></label>
            <label class="scope"><input type="checkbox" name="emit" [(ngModel)]="scopeSelection.DOCUMENT_EMIT"><span><strong>Emisión síncrona</strong><small>Sólo para compatibilidad controlada</small></span></label>
          </fieldset>
          @if (credential) { <p class="credential-block wide" role="status"><mat-icon>info</mat-icon>Guarda y confirma la credencial pendiente antes de crear otro productor.</p> }
          <div class="form-actions wide"><button type="button" mat-button (click)="creating=false">Cancelar</button><button type="submit" mat-raised-button color="primary" [disabled]="producerForm.invalid || saving || !!credential || selectedScopes.length===0"><mat-icon>{{ saving ? 'hourglass_top' : 'key' }}</mat-icon>{{ saving ? 'Creando…' : 'Crear y revelar credencial' }}</button></div>
        </form>
      </section>
    }

    <section class="producers-panel">
      <div class="section-heading"><div><h2>Aplicaciones productoras</h2><p>Actividad, atribución y credenciales de la empresa activa.</p></div><button type="button" mat-icon-button aria-label="Actualizar integraciones" (click)="load()" [disabled]="loading"><mat-icon>refresh</mat-icon></button></div>
      @if (error) { <div class="inline-state error" role="alert"><mat-icon>cloud_off</mat-icon><span>{{ error }}</span><button mat-button (click)="load()">Reintentar</button></div> }
      @else if (loading && producers.length===0) { <div class="inline-state"><mat-icon class="spin">progress_activity</mat-icon><span>Cargando productores…</span></div> }
      @else if (producers.length===0) { <div class="empty-state"><mat-icon>hub</mat-icon><h3>Aún no hay productores registrados</h3><p>{{ auth.canAdminister() ? 'Crea una identidad por aplicación para evitar compartir credenciales.' : 'Solicita a un Admin o Root que registre una identidad para cada aplicación productora.' }}</p>@if (auth.canAdminister()) { <button type="button" mat-raised-button color="primary" (click)="creating=true"><mat-icon>add</mat-icon>Crear productor</button> }</div> }
      @else {
        <div class="producer-list">
          @for (producer of producers; track producer.id) {
            <article class="producer-row" [attr.data-health]="producer.health">
              <div class="identity"><span class="health-dot" aria-hidden="true"></span><div><h3>{{ producer.name }}</h3><p>{{ producer.description || producer.code }}</p></div></div>
              <div class="health"><span>{{ healthLabel(producer.health) }}</span><small>{{ producer.lastUsedAt ? ('Último acceso ' + (producer.lastUsedAt | date:'short')) : 'Todavía sin conexiones' }}</small>@if (producer.health==='DEGRADED') { <small class="failure-detail">Último fallo {{ producer.lastFailureAt | date:'short' }}{{ producer.lastHttpStatus ? ' · HTTP '+producer.lastHttpStatus : '' }}. Revisa los eventos y la credencial del productor.</small> }</div>
              <div class="activity"><strong>{{ producer.totalDocuments }}</strong><span>documentos</span><small>{{ producer.totalRequests }} solicitudes · {{ producer.failureRequests }} fallidas</small></div>
              <div class="scope-list">@for(scope of producer.scopes; track scope){<span>{{ scopeLabel(scope) }}</span>}</div>
              <div class="actions">
                  <button type="button" mat-icon-button [attr.aria-label]="'Ver integración de '+producer.name" title="Ver integración" (click)="openGuide(producer)"><mat-icon>integration_instructions</mat-icon></button>
                @if (auth.canAdminister()) {
                  <button type="button" mat-icon-button [attr.aria-label]="'Rotar credencial de '+producer.name" title="Rotar credencial" (click)="rotate(producer)" [disabled]="!!credential || busyId===producer.id"><mat-icon>autorenew</mat-icon></button>
                  <button type="button" mat-icon-button [attr.aria-label]="(producer.status==='ACTIVE'?'Revocar ':'Activar ')+producer.name" [title]="producer.status==='ACTIVE'?'Revocar':'Activar'" (click)="toggle(producer)" [disabled]="!!credential || busyId===producer.id"><mat-icon>{{ producer.status==='ACTIVE' ? 'block' : 'play_circle' }}</mat-icon></button>
                }
              </div>
            </article>
          }
        </div>
      }
    </section>

    <section class="guide-panel">
      <div class="section-heading"><div><h2>Centro de integración</h2><p>Obtén la URL exacta, registra la credencial, copia cuerpos completos y compara un JSON antes de emitir.</p></div><button type="button" mat-raised-button color="primary" (click)="openGuide(null)"><mat-icon>integration_instructions</mat-icon>Abrir asistente completo</button></div>
      <div class="flow" aria-label="Flujo de integración"><span>Productor</span><mat-icon>arrow_forward</mat-icon><span>Ingesta</span><mat-icon>arrow_forward</mat-icon><span>RabbitMQ</span><mat-icon>arrow_forward</mat-icon><span>SUNAT / OSE</span><mat-icon>arrow_forward</mat-icon><span>CDR</span></div>
      <div class="channel-board">
        <div><mat-icon>https</mat-icon><span><strong>HTTP API · disponible</strong><small><code>X-Api-Key</code>, idempotencia y confirmación RabbitMQ con respuesta 202.</small></span></div>
        <div><mat-icon>lock</mat-icon><span><strong>RabbitMQ directo · privado</strong><small>No usa API key. Requiere identidad AMQP, TLS, vhost y ACL dedicados antes de exponerse.</small></span></div>
        <div><mat-icon>rule</mat-icon><span><strong>Comparador · sin emisión</strong><small>Marca la ruta JSON, el valor recibido, el esperado y una corrección concreta.</small></span></div>
      </div>
    </section>
  `,
  styles: [`
    :host{display:block;color:var(--forestColor)}.page-heading,.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.page-heading{margin-bottom:20px}.page-heading h1{margin:0 0 5px;font-size:30px;letter-spacing:-.025em}.page-heading p,.section-heading p{margin:0;color:var(--mutedColor);line-height:1.55}.trust-strip{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:13px;background:#173317;color:#fff;padding:16px 18px;border-radius:9px;margin-bottom:20px}.trust-strip mat-icon{color:#80ef80}.trust-strip p{margin:0;color:#d8e6d8;line-height:1.5}.trust-strip strong{color:#fff}.trust-strip>span{font-size:12px;font-weight:800;white-space:nowrap;color:#80ef80}.credential-panel{display:grid;grid-template-columns:minmax(240px,1fr) minmax(220px,1.2fr) auto minmax(155px,auto) auto;align-items:center;gap:14px;background:#eaf8eb;border:1px solid #a7c9aa;border-radius:9px;padding:16px 18px;margin-bottom:20px}.credential-panel>div{display:flex;gap:10px}.credential-panel>div mat-icon{color:#317039}.credential-panel strong,.credential-panel span{display:block}.credential-panel span{font-size:12px;color:#527052}.credential-panel strong{font-size:14px;color:#173317;margin-bottom:2px}.credential-panel code{padding:10px;background:#fff;border-radius:6px;overflow:auto;font-size:12px}.stored-confirmation{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:700;color:#173317}.stored-confirmation input{accent-color:#527852}.editor-panel,.producers-panel,.guide-panel{background:#fff;border:1px solid var(--borderColor);border-radius:9px;padding:22px;margin-bottom:20px}.section-heading h2{margin:0 0 4px;font-size:18px}.editor-panel form{display:grid;grid-template-columns:1fr 1fr;gap:17px;margin-top:20px}.editor-panel label:not(.scope){display:flex;flex-direction:column;gap:7px;font-size:13px;font-weight:700}.editor-panel input:not([type=checkbox]),.editor-panel textarea{width:100%;border:1px solid #b8c9ba;border-radius:7px;padding:12px 13px;font:inherit;color:#173317;background:#fff}.editor-panel input:not([type=checkbox]){height:48px}.editor-panel input:focus,.editor-panel textarea:focus{outline:3px solid rgba(128,239,128,.35);outline-offset:1px;border-color:#527852}.editor-panel small{color:var(--mutedColor);font-weight:400}.wide{grid-column:1/-1}.editor-panel fieldset{border:0;padding:0;margin:0;display:flex;gap:12px;flex-wrap:wrap}.editor-panel legend{font-size:13px;font-weight:700;margin-bottom:8px}.scope{display:flex;align-items:flex-start;gap:8px;min-width:220px;padding:12px;border:1px solid var(--borderColor);border-radius:7px;cursor:pointer}.scope input{margin-top:3px;accent-color:#527852}.scope span,.scope strong,.scope small{display:block}.credential-block{display:flex;align-items:center;gap:8px;margin:0;padding:10px 12px;border-radius:7px;background:#fff3e6;color:#7b4b1f;font-size:12px;font-weight:700}.credential-block mat-icon{width:18px;height:18px;font-size:18px}.form-actions{display:flex;justify-content:flex-end;gap:8px}.producer-list{margin-top:18px}.producer-row{display:grid;grid-template-columns:minmax(220px,1.4fr) minmax(180px,1fr) 125px minmax(170px,1fr) auto;align-items:center;gap:18px;padding:17px 0;border-top:1px solid var(--borderColor)}.identity{display:flex;align-items:center;gap:11px}.health-dot{width:10px;height:10px;border-radius:50%;background:#8cbd8c;box-shadow:0 0 0 4px #edf6ee;flex:0 0 auto}.producer-row[data-health=DEGRADED] .health-dot,.producer-row[data-health=REVOKED] .health-dot{background:#b55b50;box-shadow:0 0 0 4px #fbeceb}.producer-row[data-health=PENDING] .health-dot,.producer-row[data-health=IDLE] .health-dot{background:#b9793f;box-shadow:0 0 0 4px #fff3e6}.identity h3{margin:0 0 3px;font-size:15px}.identity p,.health small,.activity small{margin:0;color:var(--mutedColor);font-size:11px;line-height:1.4}.health span{display:block;font-size:12px;font-weight:800}.health .failure-detail{display:block;margin-top:5px;color:#9d332c}.activity strong,.activity span,.activity small{display:block}.activity strong{font-size:20px;font-variant-numeric:tabular-nums}.activity span{font-size:11px;font-weight:700}.scope-list{display:flex;gap:5px;flex-wrap:wrap}.scope-list span,.doc-badge{padding:4px 6px;background:#f1f6f1;border-radius:5px;color:#3f6244;font-size:9px;font-weight:800;text-transform:uppercase;letter-spacing:.04em}.actions{display:flex}.inline-state,.empty-state{display:flex;align-items:center;justify-content:center;gap:10px;min-height:140px;color:var(--mutedColor)}.inline-state.error{color:#9d332c}.empty-state{flex-direction:column;text-align:center}.empty-state mat-icon{width:42px;height:42px;font-size:42px;color:#8cbd8c}.empty-state h3,.empty-state p{margin:0}.spin{animation:spin 1.1s linear infinite}.flow{display:flex;align-items:center;justify-content:center;gap:10px;background:#173317;color:#fff;border-radius:8px;padding:14px;margin:20px 0}.flow span{font-size:12px;font-weight:800}.flow mat-icon{width:17px;height:17px;font-size:17px;color:#80ef80}.channel-board{display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px}.channel-board>div{display:flex;align-items:flex-start;gap:10px;padding:13px;background:#f3f7f3;border-radius:7px}.channel-board mat-icon{color:#527852}.channel-board span,.channel-board strong,.channel-board small{display:block}.channel-board strong{font-size:12px}.channel-board small{margin-top:4px;color:#687a6b;line-height:1.45}.channel-board code{font-size:10px}@keyframes spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.spin{animation:none}}@media(max-width:1050px){.producer-row{grid-template-columns:1.4fr 1fr 110px auto}.scope-list{grid-column:1/4}.actions{grid-column:4;grid-row:1/3}.credential-panel{grid-template-columns:1fr 1fr auto}.channel-board{grid-template-columns:1fr}}@media(max-width:760px){.page-heading,.section-heading{flex-direction:column;align-items:stretch}.trust-strip{grid-template-columns:28px 1fr}.trust-strip>span{grid-column:2;white-space:normal}.credential-panel{grid-template-columns:1fr}.editor-panel form{grid-template-columns:1fr}.wide{grid-column:auto}.producer-row{grid-template-columns:1fr auto;gap:12px}.health,.activity,.scope-list{grid-column:1}.actions{grid-column:2;grid-row:1/5}.flow{justify-content:flex-start;overflow-x:auto}.flow span{white-space:nowrap}}@media(max-width:480px){.editor-panel,.producers-panel,.guide-panel{padding:17px}.trust-strip{padding:14px}.credential-panel{padding:14px}}
  `]
})
export class IntegrationsComponent implements OnInit {
  producers: ProducerIntegration[] = [];
  loading = false; saving = false; creating = false; busyId = ''; error = '';
  credential: ProducerCredential | null = null;
  credentialStored = false;
  draft = { code: '', name: '', description: '' };
  scopeSelection: Record<ProducerScope, boolean> = { DOCUMENT_INGEST: true, DOCUMENT_READ: true, DOCUMENT_EMIT: false };
  constructor(private api: CaraboApiService, public auth: AuthService, private snack: MatSnackBar, private dialog: MatDialog) {}
  ngOnInit(): void { this.load(); }
  @HostListener('window:beforeunload', ['$event'])
  protectPendingCredential(event: BeforeUnloadEvent): void { if(this.credential&&!this.credentialStored){event.preventDefault();event.returnValue='';} }
  canLeaveWithCredential(): boolean { if(!this.credential||this.credentialStored)return true;this.snack.open('Confirma “Ya la guardé” antes de salir para no perder la credencial.','Cerrar',{duration:5000});return false; }
  load(): void { this.loading=true;this.error='';this.api.listProducerIntegrations().subscribe({next:v=>{this.producers=v;this.loading=false},error:()=>{this.error='No pudimos consultar las integraciones de la empresa activa.';this.loading=false}}); }
  get selectedScopes(): ProducerScope[] { return (Object.keys(this.scopeSelection) as ProducerScope[]).filter(scope=>this.scopeSelection[scope]); }
  get activeCount(): number { return this.producers.filter(p=>p.status==='ACTIVE').length; }
  get degradedCount(): number { return this.producers.filter(p=>p.health==='DEGRADED').length; }
  get revokedCount(): number { return this.producers.filter(p=>p.status==='REVOKED').length; }
  create(): void {
    if(this.saving||this.credential||this.selectedScopes.length===0)return;this.saving=true;
    this.api.createProducerIntegration({...this.draft,scopes:this.selectedScopes}).subscribe({next:result=>{this.showCredential(result);this.producers=[result.producer,...this.producers];this.draft={code:'',name:'',description:''};this.resetScopes();this.creating=false;this.saving=false},error:error=>{this.saving=false;this.snack.open(error?.error?.error||'No se pudo crear el productor','Cerrar',{duration:5000})}});
  }
  rotate(producer: ProducerIntegration): void { if(this.busyId||this.credential||!window.confirm(`Rotar la credencial de ${producer.name} invalidará inmediatamente la clave anterior. Confirma que puedes actualizar el secret store del productor.`))return;this.busyId=producer.id;this.api.rotateProducerCredential(producer.id).subscribe({next:result=>{this.showCredential(result);this.replace(result.producer);this.busyId=''},error:()=>{this.busyId='';this.snack.open('No se pudo rotar la credencial','Cerrar',{duration:4000})}}); }
  toggle(producer: ProducerIntegration): void { const activating=producer.status!=='ACTIVE';const impact=activating?'volverá a permitir solicitudes de esta aplicación':`detendrá inmediatamente las solicitudes de ${producer.name}`;if(this.busyId||this.credential||!window.confirm(`${activating?'Activar':'Revocar'} ${producer.name} ${impact}. ¿Deseas continuar?`))return;this.busyId=producer.id;this.api.setProducerIntegrationActive(producer.id,activating).subscribe({next:updated=>{this.replace(updated);this.busyId=''},error:()=>{this.busyId='';this.snack.open('No se pudo cambiar el estado','Cerrar',{duration:4000})}}); }
  copyCredential(): void { if(!this.credential)return;const clipboard=navigator.clipboard;if(!clipboard){this.snack.open('El portapapeles no está disponible. Copia manualmente la credencial.','Cerrar',{duration:4500});return}clipboard.writeText(this.credential.apiKey).then(()=>this.snack.open('Credencial copiada. Guárdala ahora en el secret store.','Cerrar',{duration:3500})).catch(()=>this.snack.open('El navegador rechazó el portapapeles. Selecciona y copia la credencial manualmente.','Cerrar',{duration:5000})); }
  showCredential(value: ProducerCredential): void { this.credential=value;this.credentialStored=false; }
  dismissCredential(): void { if(!this.credentialStored)return;this.credential=null;this.credentialStored=false; }
  resetScopes(): void { this.scopeSelection={DOCUMENT_INGEST:true,DOCUMENT_READ:true,DOCUMENT_EMIT:false}; }
  openGuide(producer: ProducerIntegration | null): void {
    const apiBaseUrl = new URL(environment.apiUrl, window.location.origin).toString().replace(/\/$/, '');
    this.dialog.open(IntegrationGuideDialogComponent, {
      data: { producer, apiBaseUrl, canAdminister: this.auth.canAdminister() },
      autoFocus: 'dialog', restoreFocus: true, maxWidth: 'none', panelClass: 'carabo-integration-dialog'
    });
  }
  replace(updated: ProducerIntegration): void { this.producers=this.producers.map(item=>item.id===updated.id?updated:item); }
  healthLabel(value: ProducerIntegration['health']): string { return ({HEALTHY:'Operativa',PENDING:'Pendiente de conexión',IDLE:'Sin actividad reciente',DEGRADED:'Requiere atención',REVOKED:'Revocada'} as Record<string,string>)[value]; }
  scopeLabel(value: ProducerScope): string { return ({DOCUMENT_INGEST:'Enviar',DOCUMENT_READ:'Consultar',DOCUMENT_EMIT:'Síncrona'} as Record<ProducerScope,string>)[value]; }
}
