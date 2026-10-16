import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-code-block',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  template: `<div class="code-toolbar"><button type="button" mat-button (click)="copyCode.emit()"><mat-icon>content_copy</mat-icon>{{ label }}</button></div><pre><code>{{ code }}</code></pre>`,
  styles: [`:host{display:block;min-width:0}.code-toolbar{display:flex;justify-content:flex-end;height:35px;background:#244624;border-radius:7px 7px 0 0}.code-toolbar button{color:#dff0df;font-size:11px}pre{max-height:390px;overflow:auto;margin:0;padding:16px;background:#173317;color:#edf7ed;border-radius:0 0 7px 7px;font:12px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre}code{font:inherit}`]
})
export class CodeBlockComponent {
  @Input({ required: true }) code = '';
  @Input({ required: true }) label = '';
  @Output() copyCode = new EventEmitter<void>();
}
