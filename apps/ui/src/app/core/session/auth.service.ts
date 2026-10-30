import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { DestroyRef, Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, of, switchMap, tap, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';

import { SessionContextService } from './session-context.service';

const TOKEN_KEY = 'carabo.accessToken';
const SESSION_KEY = 'carabo.session';

export interface AuthUser {
  userCode: string;
  firstName: string;
  lastName: string;
  email: string;
}
export interface UserBusiness {
  businessCode: string;
  businessName: string;
  defaultRoleCode: string;
  isActive: boolean;
}
interface AvailableBusiness extends UserBusiness {
  roles: string[];
}
interface ApplicationRole {
  businessCode: string;
  roleCode: string;
}
interface PermissionNode {
  elementCode: string;
  children?: PermissionNode[];
}
interface AuthApplication {
  app: string;
  roles: ApplicationRole[];
  permissions?: PermissionNode[];
}

export interface LoginResponse {
  success: boolean;
  data: {
    auth: { token: string; expiresAt?: string };
    user: AuthUser;
    businesses: Array<{
      businessCode: string;
      defaultRoleCode: string;
      isActive: boolean;
      business?: { businessName?: string };
    }>;
    applications?: AuthApplication[];
    licenseType?: 'root' | 'professional' | 'applicative';
  };
}

export interface AuthSession {
  user: AuthUser;
  tenantId: string;
  roles: string[];
  permissions: string[];
  licenseType: 'root' | 'professional' | 'applicative';
  businesses: UserBusiness[];
  businessRoles: Record<string, string[]>;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly context = inject(SessionContextService);
  private readonly router = inject(Router);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly accessTokenState = signal<string | null>(this.read(TOKEN_KEY));
  readonly accessToken = this.accessTokenState.asReadonly();
  private readonly sessionState = signal<AuthSession | null>(this.readSession());
  readonly session = this.sessionState.asReadonly();
  private readonly sessionExpiredState = signal(false);
  readonly sessionExpired = this.sessionExpiredState.asReadonly();
  private readonly businessesLoadedState = signal(false);
  readonly businessesLoaded = this.businessesLoadedState.asReadonly();

  constructor() {
    const session = this.session();
    if (this.accessToken() && session)
      this.context.activate(session.user.userCode, session.tenantId);
    this.scheduleExpiry(this.accessToken());
    inject(DestroyRef).onDestroy(() => this.clearExpiryTimer());
  }

  login(user: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(
        `${environment.authUrl}/auth/login`,
        { user: user.trim(), password },
        { headers: new HttpHeaders({ signature: environment.authSignature }) },
      )
      .pipe(
        timeout(15000),
        switchMap((response) => {
          if (!response.success) throw new Error('No se pudo iniciar sesión.');
          return this.loadBusinesses(response.data.auth.token).pipe(
            tap((businesses) => this.applySession(response, businesses)),
            map(() => response),
          );
        }),
      );
  }

  /** Refresh saved sessions too: cached login roles are not the company catalog. */
  refreshBusinesses(): Observable<boolean> {
    const token = this.accessToken();
    if (!token || !this.session()) return of(false);
    this.businessesLoadedState.set(false);
    return this.loadBusinesses(token).pipe(
      map((businesses) => {
        const current = this.session();
        if (!current || this.accessToken() !== token) return false;
        const updated = this.businessContext(businesses, current.tenantId);
        this.persistSession({ ...current, ...updated });
        this.businessesLoadedState.set(true);
        return updated.tenantId !== current.tenantId;
      }),
    );
  }

  private loadBusinesses(token: string): Observable<AvailableBusiness[]> {
    return this.http
      .get<AvailableBusiness[]>(`${environment.apiUrl}/access/businesses`, {
        headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
      })
      .pipe(timeout(15000));
  }

  private businessContext(available: AvailableBusiness[], previousTenant?: string) {
    const businesses = available.filter((business) => business.isActive);
    if (!businesses.length)
      throw new Error('Tu usuario no tiene una empresa habilitada para Carabo.');
    const businessRoles = Object.fromEntries(
      businesses.map((business) => [business.businessCode, business.roles]),
    );
    const tenantId = businesses.some((business) => business.businessCode === previousTenant)
      ? previousTenant!
      : businesses[0].businessCode;
    return { businesses, businessRoles, tenantId, roles: businessRoles[tenantId] };
  }

  selectBusiness(businessCode: string): boolean {
    const current = this.session();
    if (!current || !current.businesses.some((business) => business.businessCode === businessCode))
      return false;
    this.persistSession({
      ...current,
      tenantId: businessCode,
      roles: this.rolesForBusiness(current, businessCode),
    });
    return true;
  }

  logout(): void {
    this.clearExpiryTimer();
    this.context.activate();
    if (this.isBrowser) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(SESSION_KEY);
    }
    this.accessTokenState.set(null);
    this.sessionState.set(null);
    this.sessionExpiredState.set(false);
    this.businessesLoadedState.set(false);
    void this.router.navigateByUrl('/login');
  }

  requireReauthentication(): void {
    if (this.sessionExpired()) return;
    this.clearExpiryTimer();
    this.context.activate();
    if (this.isBrowser) localStorage.removeItem(TOKEN_KEY);
    this.accessTokenState.set(null);
    this.sessionExpiredState.set(true);
    void this.router.navigateByUrl('/login');
  }

  isAuthenticated(): boolean {
    return Boolean(this.accessToken() && this.session()?.tenantId && !this.sessionExpired());
  }
  isRoot(): boolean {
    return this.session()?.licenseType === 'root';
  }

  hasAnyRole(...roles: string[]): boolean {
    if (this.isRoot()) return true;
    const current = new Set((this.session()?.roles ?? []).map((role) => role.toUpperCase()));
    return roles.some((role) => current.has(role.toUpperCase()));
  }

  canAdminister(): boolean {
    return this.hasAnyRole('ADMIN_Carabo', 'ROOT_PLATFORM');
  }
  canSupervise(): boolean {
    return this.hasAnyRole('SUPERVISOR_Carabo', 'ADMIN_Carabo', 'ROOT_PLATFORM');
  }
  hasPermission(elementCode: string): boolean {
    return this.isRoot() || (this.session()?.permissions.includes(elementCode) ?? false);
  }

  private applySession(response: LoginResponse, available: AvailableBusiness[]): void {
    if (!response.success || !this.isBrowser) return;
    const carabo = (response.data.applications ?? []).find(
      (app) => app.app.toLowerCase() === 'carabo',
    );
    const session: AuthSession = {
      user: response.data.user,
      ...this.businessContext(available, this.session()?.tenantId),
      permissions: this.flattenPermissions(carabo?.permissions ?? []),
      licenseType: response.data.licenseType ?? 'applicative',
    };
    localStorage.setItem(TOKEN_KEY, response.data.auth.token);
    this.accessTokenState.set(response.data.auth.token);
    this.sessionExpiredState.set(false);
    this.persistSession(session);
    this.scheduleExpiry(response.data.auth.token);
    this.businessesLoadedState.set(true);
  }

  private rolesForBusiness(session: AuthSession, businessCode: string): string[] {
    return session.licenseType === 'root'
      ? ['ROOT_PLATFORM']
      : [...(session.businessRoles[businessCode] ?? [])];
  }
  private flattenPermissions(nodes: PermissionNode[]): string[] {
    return nodes.flatMap((node) => [
      node.elementCode,
      ...this.flattenPermissions(node.children ?? []),
    ]);
  }
  private persistSession(session: AuthSession): void {
    if (this.isBrowser) localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    const previous = this.session();
    this.sessionState.set(session);
    if (
      previous?.user.userCode !== session.user.userCode ||
      previous?.tenantId !== session.tenantId
    )
      this.context.activate(session.user.userCode, session.tenantId);
  }
  private read(key: string): string | null {
    if (!this.isBrowser) return null;
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }
  private readSession(): AuthSession | null {
    const raw = this.read(SESSION_KEY);
    if (!raw) return null;
    try {
      const session = JSON.parse(raw) as AuthSession;
      return session.user && session.tenantId ? session : null;
    } catch {
      return null;
    }
  }
  private scheduleExpiry(token: string | null): void {
    this.clearExpiryTimer();
    const expiresAt = this.readTokenExpiry(token);
    if (!expiresAt) return;
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      queueMicrotask(() => this.requireReauthentication());
      return;
    }
    const maxDelay = 2_147_000_000;
    this.expiryTimer = setTimeout(
      () =>
        remaining > maxDelay
          ? this.scheduleExpiry(this.accessToken())
          : this.requireReauthentication(),
      Math.min(remaining, maxDelay),
    );
  }
  private readTokenExpiry(token: string | null): number | null {
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const decoded = JSON.parse(
        atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')),
      ) as { exp?: number };
      return typeof decoded.exp === 'number' ? decoded.exp * 1000 : null;
    } catch {
      return null;
    }
  }
  private clearExpiryTimer(): void {
    if (this.expiryTimer !== null) clearTimeout(this.expiryTimer);
    this.expiryTimer = null;
  }
}
