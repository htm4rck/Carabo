import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { AuthService } from './auth.service';
import { AmaziliaHandoff, amaziliaHandoff, amaziliaReturn } from './amazilia-handoff';
@Injectable({ providedIn: 'root' })
export class AmaziliaHandoffService {
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly auth = inject(AuthService);
  private readonly key = 'carabo.amazilia-handoff.v1';
  readonly context = signal<AmaziliaHandoff | null>(this.read());
  remember(value: unknown): boolean {
    const context = amaziliaHandoff(value);
    if (!context || !this.browser) return false;
    try {
      sessionStorage.setItem(this.key, JSON.stringify(context));
      this.context.set(context);
      return true;
    } catch {
      return false;
    }
  }
  loginDestination(): string {
    return this.context()
      ? this.context()?.application === 'birdport'
        ? '/companion/birdport'
        : '/companion/amazilia'
      : '/';
  }
  destination(): string | null {
    const context = this.context();
    if (
      !context ||
      context.userCode !== this.auth.session()?.user.userCode ||
      !this.auth.selectBusiness(context.businessCode)
    )
      return null;
    return '/documents';
  }
  active(): AmaziliaHandoff | null {
    const context = this.context(),
      session = this.auth.session();
    return context &&
      session &&
      context.userCode === session.user.userCode &&
      context.businessCode === session.tenantId
      ? context
      : null;
  }
  returnUrl(): string {
    const context = this.active();
    return context ? amaziliaReturn(context) : '';
  }
  private read(): AmaziliaHandoff | null {
    if (!this.browser) return null;
    try {
      return amaziliaHandoff(JSON.parse(sessionStorage.getItem(this.key) || 'null'));
    } catch {
      return null;
    }
  }
}
