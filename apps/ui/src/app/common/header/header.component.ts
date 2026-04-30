import { Component } from '@angular/core';
import { ToggleService } from './toggle.service';
import { NgClass, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [NgClass, DatePipe, RouterLink],
  template: `
    <header class="header-area bg-white border-bottom-radius box-shadow" [ngClass]="{'active': isToggled}">
      <div class="d-md-flex align-items-center justify-content-space-between">
        <div class="header-left-side d-flex align-items-center">
          <div class="burger-menu" (click)="toggle()" [ngClass]="{'active': isToggled}">
            <span class="top-bar"></span>
            <span class="middle-bar"></span>
            <span class="bottom-bar"></span>
          </div>
          <form class="search-box position-relative">
            <input type="text" class="input-search d-block" placeholder="Buscar...">
            <button type="submit"><i class="ri-search-line"></i></button>
          </form>
        </div>
        <ul class="header-right-side d-flex align-items-center mt-0 mb-0">
          <li class="d-none d-lg-block">
            <div class="date-btn main-color fw-semibold position-relative">
              <i class="ri-calendar-line"></i> {{ currentDate | date: 'dd MMMM yyyy' }}
            </div>
          </li>
          <li>
            <span class="fw-semibold">🦉 Carabo</span>
          </li>
        </ul>
      </div>
    </header>
  `,
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {
  isToggled = false;
  currentDate = new Date();

  constructor(private toggleService: ToggleService) {
    this.toggleService.isToggled$.subscribe(v => this.isToggled = v);
  }

  toggle() { this.toggleService.toggle(); }
}
