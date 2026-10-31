import {
  Component,
  DestroyRef,
  HostListener,
  OnInit,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ToggleService } from './toggle.service';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { AuthService } from '../../core/session/auth.service';
import { BuildInfoService } from '../../core/configuration/build-info.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [NgClass, FormsModule],
  templateUrl: './header.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  readonly businessError = signal('');
  desktopCollapsed = false;
  mobileOpen = false;
  isMobile = window.innerWidth < 1200;
  currentDate = new Date();

  constructor(
    private toggleService: ToggleService,
    public auth: AuthService,
    public buildInfo: BuildInfoService,
  ) {
    this.toggleService.desktopCollapsed$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => (this.desktopCollapsed = value));
    this.toggleService.mobileOpen$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => (this.mobileOpen = value));
  }

  ngOnInit(): void {
    this.refreshBusinesses();
  }

  refreshBusinesses(): void {
    this.businessError.set('');
    this.auth
      .refreshBusinesses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (changed) => {
          if (changed) window.location.reload();
        },
        error: () => this.businessError.set('No pudimos actualizar las empresas disponibles.'),
      });
  }

  @HostListener('window:resize')
  onViewportChange(): void {
    const wasMobile = this.isMobile;
    this.isMobile = window.innerWidth < 1200;
    if (wasMobile && !this.isMobile) this.toggleService.closeMobile();
  }

  toggleNavigation(): void {
    if (this.isMobile) {
      const willOpen = !this.mobileOpen;
      this.toggleService.toggleMobile();
      if (willOpen)
        queueMicrotask(() =>
          document.querySelector<HTMLButtonElement>('.sidebar-area .drawer-close')?.focus(),
        );
      return;
    }
    this.toggleService.toggleDesktop();
  }

  get navigationLabel(): string {
    if (this.isMobile) return this.mobileOpen ? 'Cerrar menú principal' : 'Abrir menú principal';
    return this.desktopCollapsed ? 'Expandir menú principal' : 'Contraer menú principal';
  }
  shortCommit(commit: string): string {
    return commit && commit !== 'local' ? commit.slice(0, 7) : commit || 'sin revisión';
  }

  get displayName(): string {
    const user = this.auth.session()?.user;
    return user
      ? `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.userCode
      : 'Usuario';
  }
  get initials(): string {
    const user = this.auth.session()?.user;
    return `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase() || 'U';
  }
  get activeRole(): string {
    const role = this.auth.session()?.roles?.[0] ?? 'Acceso fiscal';
    return role.replace('_Carabo', '').replace('_PLATFORM', '').toLowerCase();
  }
  changeBusiness(code: string): void {
    if (this.auth.selectBusiness(code)) window.location.reload();
  }
}
