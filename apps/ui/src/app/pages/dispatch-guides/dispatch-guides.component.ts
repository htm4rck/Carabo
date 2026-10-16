import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { finalize } from 'rxjs';
import { CaraboApiService, DispatchGuideSummary } from '../../services/carabo-api.service';

@Component({
  selector: 'app-dispatch-guides', standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatSnackBarModule, MatTableModule],
  template: `
    <div class="breadcrumb-area"><h1>Guías de remisión</h1><p>Seguimiento SUNAT, XML firmado, CDR y representación impresa.</p></div>
    <mat-card><mat-card-content>
      <div class="table-scroll"><table mat-table [dataSource]="guides" aria-label="Guías de remisión">
        <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>Número</th><td mat-cell *matCellDef="let g"><strong>{{g.fullNumber}}</strong><small>{{g.issueDate}}</small></td></ng-container>
        <ng-container matColumnDef="receiver"><th mat-header-cell *matHeaderCellDef>Destinatario</th><td mat-cell *matCellDef="let g">{{g.receiverName}}<small>{{g.receiverDoc}}</small></td></ng-container>
        <ng-container matColumnDef="route"><th mat-header-cell *matHeaderCellDef>Traslado</th><td mat-cell *matCellDef="let g">{{g.originAddress}}<small>→ {{g.destinationAddress}}</small></td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let g"><span class="badge" [attr.data-status]="g.status">{{g.status}}</span><small>{{g.sunatResponse || 'Sin respuesta SUNAT'}}</small></td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Acciones</th><td mat-cell *matCellDef="let g" class="actions">
          @if (g.ticket) { <button mat-icon-button type="button" aria-label="Consultar ticket SUNAT" (click)="refresh(g)" [disabled]="busyId===g.id"><mat-icon>{{busyId===g.id?'hourglass_top':'sync'}}</mat-icon></button> }
          <button mat-icon-button type="button" aria-label="Descargar XML" (click)="download(g,'xml')"><mat-icon>code</mat-icon></button>
          <button mat-icon-button type="button" aria-label="Descargar CDR" (click)="download(g,'cdr')"><mat-icon>verified</mat-icon></button>
          <button mat-icon-button type="button" aria-label="Descargar PDF" (click)="download(g,'pdf')"><mat-icon>picture_as_pdf</mat-icon></button>
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr><tr mat-row *matRowDef="let row; columns:columns"></tr>
      </table></div>
      @if (!loading && guides.length===0) { <div class="empty"><mat-icon>local_shipping</mat-icon><h2>Aún no hay guías</h2><p>Las GRE emitidas por API aparecerán aquí.</p></div> }
    </mat-card-content></mat-card>`,
  styles: [`
    .breadcrumb-area{margin-bottom:24px}.breadcrumb-area h1{margin:0 0 4px;font-size:22px}.breadcrumb-area p,td small{display:block;color:var(--mutedColor);margin:0}mat-card-content{padding:20px}.table-scroll{overflow:auto}table{width:100%;min-width:850px}.badge{display:inline-block;padding:4px 10px;border-radius:12px;background:#edf3ed;color:#173317;font-weight:700;font-size:12px}.badge[data-status="ACCEPTED"]{background:#e8f5e9;color:#236628}.badge[data-status="ERROR"]{background:#ffebee;color:#a51d2d}.actions{white-space:nowrap}.empty{min-height:220px;display:grid;place-content:center;text-align:center;color:var(--mutedColor)}.empty mat-icon{margin:auto;font-size:48px;width:48px;height:48px;color:var(--accentColor)}.empty h2{color:var(--forestColor);margin:10px 0 4px}
  `]
})
export class DispatchGuidesComponent implements OnInit {
  columns = ['number','receiver','route','status','actions']; guides: DispatchGuideSummary[] = []; loading = true; busyId = '';
  constructor(private api: CaraboApiService, private snack: MatSnackBar) {}
  ngOnInit(){ this.api.listDispatchGuides().pipe(finalize(()=>this.loading=false)).subscribe({next:v=>this.guides=v,error:()=>this.snack.open('No se pudieron cargar las guías','Cerrar',{duration:4000})}); }
  refresh(g: DispatchGuideSummary){ this.busyId=g.id; this.api.checkDispatchGuideStatus(g.id).pipe(finalize(()=>this.busyId='')).subscribe({next:v=>{Object.assign(g,v);this.snack.open(v.sunatResponse||'Estado actualizado','Cerrar',{duration:4000});},error:()=>this.snack.open('No se pudo consultar SUNAT','Cerrar',{duration:4000})}); }
  download(g: DispatchGuideSummary,type:'xml'|'cdr'|'pdf'){ this.api.downloadDispatchGuide(g.id,type).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`${g.fullNumber}.${type==='pdf'?'pdf':'xml'}`;a.click();URL.revokeObjectURL(url);},error:()=>this.snack.open(`No hay ${type.toUpperCase()} disponible`,'Cerrar',{duration:3500})}); }
}
