import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { CaraboApiService, NotificationSummary } from '../../services/carabo-api.service';

@Component({selector:'app-operations',standalone:true,imports:[CommonModule,MatCardModule,MatIconModule,MatTableModule],template:`
  <div class="breadcrumb-area"><h1>Entregas y webhooks</h1><p>Trazabilidad de notificaciones a sistemas productores.</p></div>
  <mat-card><mat-card-content><div class="summary"><div><strong>{{delivered}}</strong><span>Entregadas</span></div><div><strong>{{pending}}</strong><span>Pendientes/reintento</span></div><div><strong>{{dead}}</strong><span>Agotadas</span></div></div>
  <div class="table-scroll"><table mat-table [dataSource]="items" aria-label="Entregas de callbacks y webhooks">
    <ng-container matColumnDef="created"><th mat-header-cell *matHeaderCellDef>Creada</th><td mat-cell *matCellDef="let n">{{n.createdAt|date:'medium'}}</td></ng-container>
    <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Canal</th><td mat-cell *matCellDef="let n">{{n.type}}</td></ng-container>
    <ng-container matColumnDef="document"><th mat-header-cell *matHeaderCellDef>Documento</th><td mat-cell *matCellDef="let n"><code>{{n.documentId}}</code></td></ng-container>
    <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let n"><span class="badge" [attr.data-status]="n.status">{{n.status}}</span></td></ng-container>
    <ng-container matColumnDef="attempts"><th mat-header-cell *matHeaderCellDef>Intentos</th><td mat-cell *matCellDef="let n">{{n.attempts}}</td></ng-container>
    <ng-container matColumnDef="error"><th mat-header-cell *matHeaderCellDef>Último resultado</th><td mat-cell *matCellDef="let n">{{n.lastError||'Entrega confirmada'}}</td></ng-container>
    <tr mat-header-row *matHeaderRowDef="columns"></tr><tr mat-row *matRowDef="let row;columns:columns"></tr>
  </table></div></mat-card-content></mat-card>`,styles:[`.breadcrumb-area{margin-bottom:24px}.breadcrumb-area h1{margin:0 0 4px;font-size:22px}.breadcrumb-area p{margin:0;color:var(--mutedColor)}mat-card-content{padding:20px}.summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:20px}.summary div{padding:16px;border:1px solid #dce5dc;border-radius:10px}.summary strong,.summary span{display:block}.summary strong{font-size:24px;color:#173317}.summary span{color:var(--mutedColor)}.table-scroll{overflow:auto}table{width:100%;min-width:800px}.badge{padding:4px 10px;border-radius:12px;background:#fff3e0;color:#7a4b00;font-weight:700;font-size:12px}.badge[data-status="DELIVERED"]{background:#e8f5e9;color:#236628}.badge[data-status="DEAD"]{background:#ffebee;color:#a51d2d}@media(max-width:600px){.summary{grid-template-columns:1fr}}`]})
export class OperationsComponent implements OnInit { columns=['created','type','document','status','attempts','error'];items:NotificationSummary[]=[];constructor(private api:CaraboApiService){}ngOnInit(){this.api.listNotifications().subscribe(v=>this.items=v)}get delivered(){return this.items.filter(i=>i.status==='DELIVERED').length}get pending(){return this.items.filter(i=>i.status==='PENDING'||i.status==='RETRY').length}get dead(){return this.items.filter(i=>i.status==='DEAD').length}}
