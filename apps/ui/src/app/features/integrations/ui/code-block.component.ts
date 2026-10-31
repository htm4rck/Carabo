import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-code-block',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './code-block.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './code-block.component.scss',
})
export class CodeBlockComponent {
  @Input({ required: true }) code = '';
  @Input({ required: true }) label = '';
  @Output() copyCode = new EventEmitter<void>();
}
