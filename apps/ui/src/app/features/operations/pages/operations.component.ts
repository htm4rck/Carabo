import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, DestroyRef } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { OperationsApi as CaraboApiService } from '../data-access/operations-api.service';
import type { AsyncMessageSummary, NotificationSummary } from '../domain/operations.models';

@Component({
  selector: 'app-operations',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './operations.component.html',
  styleUrl: './operations.component.scss',
})
export class OperationsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  columns = ['created', 'type', 'document', 'status', 'attempts', 'error', 'actions'];
  messageColumns = [
    'updated',
    'external',
    'messageStatus',
    'counts',
    'messageError',
    'messageActions',
  ];
  items: NotificationSummary[] = [];
  messages: AsyncMessageSummary[] = [];
  constructor(
    private api: CaraboApiService,
    private snack: MatSnackBar,
  ) {}
  ngOnInit() {
    this.load();
  }
  load() {
    this.api
      .listNotifications()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => (this.items = v));
    this.api
      .listAsyncMessages()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => (this.messages = v));
  }
  retry(item: NotificationSummary) {
    this.api
      .retryNotification(item.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          item.status = 'RETRY';
          item.attempts = 0;
          item.lastError = null;
          this.snack.open('Entrega reactivada', 'Cerrar', { duration: 3000 });
        },
        error: () =>
          this.snack.open('No se pudo reactivar la entrega', 'Cerrar', { duration: 4000 }),
      });
  }
  replay(item: AsyncMessageSummary) {
    this.api
      .replayAsyncMessage(item.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          item.status = 'REPLAYED';
          item.replayCount++;
          this.snack.open('Replay confirmado por RabbitMQ', 'Cerrar', { duration: 3500 });
        },
        error: () =>
          this.snack.open('RabbitMQ no confirmó el replay', 'Cerrar', { duration: 4500 }),
      });
  }
  get delivered() {
    return this.items.filter((i) => i.status === 'DELIVERED').length;
  }
  get pending() {
    return this.items.filter((i) => i.status === 'PENDING' || i.status === 'RETRY').length;
  }
  get dead() {
    return this.items.filter((i) => i.status === 'DEAD').length;
  }
}
