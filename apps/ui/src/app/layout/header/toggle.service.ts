import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ToggleService {
  private readonly desktopCollapsed = new BehaviorSubject<boolean>(false);
  private readonly mobileOpen = new BehaviorSubject<boolean>(false);

  readonly desktopCollapsed$ = this.desktopCollapsed.asObservable();
  readonly mobileOpen$ = this.mobileOpen.asObservable();

  toggleDesktop(): void {
    this.desktopCollapsed.next(!this.desktopCollapsed.value);
  }

  toggleMobile(): void {
    this.mobileOpen.next(!this.mobileOpen.value);
  }

  closeMobile(): void {
    this.mobileOpen.next(false);
  }
}
