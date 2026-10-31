import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, DestroyRef, ChangeDetectionStrategy } from '@angular/core';
import { ToggleService } from '../header/toggle.service';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass } from '@angular/common';
import { NgScrollbarModule } from 'ngx-scrollbar';
import { MatExpansionModule } from '@angular/material/expansion';
import { AuthService } from '../../core/session/auth.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgClass, NgScrollbarModule, MatExpansionModule],
  templateUrl: './sidebar.component.html',
  // eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection -- Preserve pre-v22 async rendering during this framework migration.
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  private readonly destroyRef = inject(DestroyRef);

  desktopCollapsed = false;
  mobileOpen = false;

  constructor(
    private toggleService: ToggleService,
    public auth: AuthService,
  ) {
    this.toggleService.desktopCollapsed$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => (this.desktopCollapsed = value));
    this.toggleService.mobileOpen$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => (this.mobileOpen = value));
  }

  closeMobile(): void {
    this.toggleService.closeMobile();
    queueMicrotask(() =>
      document.querySelector<HTMLButtonElement>('.header-area .burger-menu')?.focus(),
    );
  }

  closeAfterNavigation(event: Event): void {
    if ((event.target as HTMLElement).closest('a.sidebar-menu-link'))
      this.toggleService.closeMobile();
  }
}
