import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { ToggleService } from './header/toggle.service';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './header/header.component';
import { SidebarComponent } from './sidebar/sidebar.component';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { BuildInfoService } from '../core/configuration/build-info.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, CommonModule, SidebarComponent, HeaderComponent],
  templateUrl: './shell.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);

  desktopCollapsed = false;
  mobileOpen = false;
  isLogin = false;
  private readonly subscriptions = new Subscription();

  constructor(
    private toggleService: ToggleService,
    private router: Router,
    public buildInfo: BuildInfoService,
  ) {
    this.subscriptions.add(
      this.toggleService.desktopCollapsed$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((value) => (this.desktopCollapsed = value)),
    );
    this.subscriptions.add(
      this.toggleService.mobileOpen$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((value) => {
          this.mobileOpen = value;
          this.syncBodyScroll();
        }),
    );
    this.isLogin = this.router.url.startsWith('/login');
    this.subscriptions.add(
      this.router.events
        .pipe(filter((event) => event instanceof NavigationEnd))
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((event) => {
          this.isLogin = (event as NavigationEnd).urlAfterRedirects.startsWith('/login');
          this.toggleService.closeMobile();
          queueMicrotask(() =>
            document.getElementById('main-content')?.focus({ preventScroll: true }),
          );
        }),
    );
  }

  ngOnInit(): void {
    this.buildInfo.load();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    document.body.classList.remove('nav-drawer-open');
  }

  @HostListener('document:keydown.escape')
  closeNavigation(): void {
    if (!this.mobileOpen) return;
    this.toggleService.closeMobile();
    queueMicrotask(() =>
      document.querySelector<HTMLButtonElement>('.header-area .burger-menu')?.focus(),
    );
  }

  @HostListener('window:resize')
  onViewportChange(): void {
    this.syncBodyScroll();
  }

  private syncBodyScroll(): void {
    document.body.classList.toggle('nav-drawer-open', this.mobileOpen && window.innerWidth < 1200);
  }
}
