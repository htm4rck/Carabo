import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AmaziliaHandoffService } from '../core/session/amazilia-handoff.service';
@Component({
  selector: 'app-amazilia-return',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (handoff.active(); as context) {
    <aside class="amazilia-return" aria-label="Contexto de la aplicación de origen">
      <span
        >Desde {{ context.application === 'birdport' ? 'BirdPort' : 'Amazilia' }} ·
        {{ context.businessCode }}
        @if (context.branchCode) {
          · Tienda {{ context.branchCode }}
        }</span
      ><a [href]="handoff.returnUrl()"
        >Volver a {{ context.application === 'birdport' ? 'BirdPort' : 'Amazilia' }}</a
      >
    </aside>
  }`,
  styles: [
    `
      .amazilia-return {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        align-items: center;
        justify-content: space-between;
        padding: 0.75rem 1rem;
        background: #eef6f3;
        border: 1px solid #c5ded2;
        border-radius: 0.5rem;
        margin-bottom: 1rem;
      }
      .amazilia-return a {
        color: #145b42;
        text-decoration: underline;
      }
    `,
  ],
})
export class AmaziliaReturnComponent {
  readonly handoff = inject(AmaziliaHandoffService);
}
