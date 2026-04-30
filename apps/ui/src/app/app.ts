import { Component } from '@angular/core';
import { ToggleService } from './common/header/toggle.service';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './common/header/header.component';
import { SidebarComponent } from './common/sidebar/sidebar.component';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, SidebarComponent, HeaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class AppComponent {
  isToggled = false;

  constructor(private toggleService: ToggleService) {
    this.toggleService.isToggled$.subscribe(v => this.isToggled = v);
  }
}
