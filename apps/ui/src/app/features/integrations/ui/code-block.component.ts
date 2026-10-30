import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-code-block',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './code-block.component.html',
  styleUrl: './code-block.component.scss',
})
export class CodeBlockComponent {
  @Input({ required: true }) code = '';
  @Input({ required: true }) label = '';
  @Output() copyCode = new EventEmitter<void>();
}
