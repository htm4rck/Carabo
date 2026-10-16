import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { ToggleService } from './common/header/toggle.service';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './common/header/header.component';
import { SidebarComponent } from './common/sidebar/sidebar.component';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { BuildInfoService } from './services/build-info.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, SidebarComponent, HeaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class AppComponent implements OnInit, OnDestroy {
  desktopCollapsed = false;
  mobileOpen = false;
  isLogin = false;
  private readonly subscriptions = new Subscription();

  constructor(private toggleService: ToggleService, private router: Router, public buildInfo: BuildInfoService) {
    this.subscriptions.add(this.toggleService.desktopCollapsed$.subscribe(value => this.desktopCollapsed = value));
    this.subscriptions.add(this.toggleService.mobileOpen$.subscribe(value => {
      this.mobileOpen = value;
      this.syncBodyScroll();
    }));
    this.isLogin = this.router.url.startsWith('/login');
    this.subscriptions.add(this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(event => {
      this.isLogin = (event as NavigationEnd).urlAfterRedirects.startsWith('/login');
      this.toggleService.closeMobile();
    }));
  }

  ngOnInit(): void { this.buildInfo.load(); }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    document.body.classList.remove('nav-drawer-open');
  }

  @HostListener('document:keydown.escape')
  closeNavigation(): void {
    if (!this.mobileOpen) return;
    this.toggleService.closeMobile();
    queueMicrotask(() => document.querySelector<HTMLButtonElement>('.header-area .burger-menu')?.focus());
  }

  @HostListener('window:resize')
  onViewportChange(): void {
    this.syncBodyScroll();
  }

  private syncBodyScroll(): void {
    document.body.classList.toggle('nav-drawer-open', this.mobileOpen && window.innerWidth < 1200);
  }
}
