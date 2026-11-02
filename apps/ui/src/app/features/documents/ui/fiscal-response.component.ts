import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { DocumentSummary } from '../domain/documents.models';
import { fiscalResponse } from './fiscal-response';

@Component({
  selector: 'app-fiscal-response',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (response(); as result) {
    <div class="fiscal-response">
      @if (result.code) {
        <strong>SUNAT {{ result.code }}</strong>
      }
      <p>{{ result.message }}</p>
      @if (result.detail) {
        <details>
          <summary>Detalle de la respuesta</summary>
          <p>{{ result.detail }}</p>
        </details>
      }
    </div>
  }`,
  styles: `
    .fiscal-response {
      margin-top: 8px;
      max-width: 42ch;
      overflow-wrap: anywhere;
      white-space: normal;
      font-size: 0.875rem;
      line-height: 1.5;
      color: var(--mat-sys-error, #a42525);
    }
    p {
      margin: 4px 0;
    }
    summary {
      cursor: pointer;
      min-height: 44px;
      display: flex;
      align-items: center;
      text-decoration: underline;
    }
  `,
})
export class FiscalResponseComponent {
  readonly document = input.required<Pick<DocumentSummary, 'status' | 'sunatResponse'>>();
  readonly response = computed(() => fiscalResponse(this.document()));
}
