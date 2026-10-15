import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

const TOKEN_KEY = 'carabo.accessToken';
const SESSION_KEY = 'carabo.session';

export interface AuthUser { userCode: string; firstName: string; lastName: string; email: string; }
export interface UserBusiness { businessCode: string; businessName: string; defaultRoleCode: string; isActive: boolean; }
interface ApplicationRole { businessCode: string; roleCode: string; }
interface PermissionNode { elementCode: string; children?: PermissionNode[]; }
interface AuthApplication { app: string; roles: ApplicationRole[]; permissions?: PermissionNode[]; }

export interface LoginResponse {
  success: boolean;
  data: {
    auth: { token: string; expiresAt?: string };
    user: AuthUser;
    businesses: Array<{ businessCode: string; defaultRoleCode: string; isActive: boolean; business?: { businessName?: string } }>;
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
  private readonly router = inject(Router);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  readonly accessToken = signal<string | null>(this.read(TOKEN_KEY));
  readonly session = signal<AuthSession | null>(this.readSession());
  readonly sessionExpired = signal(false);

  constructor() { this.scheduleExpiry(this.accessToken()); }

  login(user: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${environment.authUrl}/auth/login`, { user: user.trim(), password },
      { headers: new HttpHeaders({ signature: environment.authSignature }) },
    ).pipe(tap(response => this.applySession(response)));
  }

  selectBusiness(businessCode: string): boolean {
    const current = this.session();
    if (!current || !current.businesses.some(business => business.businessCode === businessCode)) return false;
    this.persistSession({ ...current, tenantId: businessCode, roles: this.rolesForBusiness(current, businessCode) });
    return true;
  }

  logout(): void {
    this.clearExpiryTimer();
    if (this.isBrowser) { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(SESSION_KEY); }
    this.accessToken.set(null); this.session.set(null); this.sessionExpired.set(false);
    void this.router.navigateByUrl('/login');
  }

  requireReauthentication(): void {
    if (this.sessionExpired()) return;
    this.clearExpiryTimer();
    if (this.isBrowser) localStorage.removeItem(TOKEN_KEY);
    this.accessToken.set(null); this.sessionExpired.set(true);
    void this.router.navigateByUrl('/login');
  }

  isAuthenticated(): boolean { return Boolean(this.accessToken() && this.session()?.tenantId && !this.sessionExpired()); }
  isRoot(): boolean { return this.session()?.licenseType === 'root'; }

  hasAnyRole(...roles: string[]): boolean {
    if (this.isRoot()) return true;
    const current = new Set((this.session()?.roles ?? []).map(role => role.toUpperCase()));
    return roles.some(role => current.has(role.toUpperCase()));
  }

  canAdminister(): boolean { return this.hasAnyRole('ADMIN_Carabo', 'ROOT_PLATFORM'); }
  canSupervise(): boolean { return this.hasAnyRole('SUPERVISOR_Carabo', 'ADMIN_Carabo', 'ROOT_PLATFORM'); }
  hasPermission(elementCode: string): boolean { return this.isRoot() || (this.session()?.permissions.includes(elementCode) ?? false); }

  private applySession(response: LoginResponse): void {
    if (!response.success || !this.isBrowser) return;
    const carabo = (response.data.applications ?? []).find(app => app.app.toLowerCase() === 'carabo');
    const businessRoles = (carabo?.roles ?? []).reduce<Record<string, string[]>>((result, role) => {
      if (role.businessCode) result[role.businessCode] = [...new Set([...(result[role.businessCode] ?? []), role.roleCode])];
      return result;
    }, {});
    const isRoot = response.data.licenseType === 'root';
    const caraboBusinessCodes = new Set(Object.keys(businessRoles));
    const businesses = response.data.businesses
      .filter(business => business.isActive && Boolean(businessRoles[business.businessCode]?.length)
        && (!isRoot || caraboBusinessCodes.has(business.businessCode)))
      .map(business => ({
        businessCode: business.businessCode,
        businessName: business.business?.businessName || business.businessCode,
        defaultRoleCode: business.defaultRoleCode,
        isActive: business.isActive,
      }));
    if (businesses.length === 0) throw new Error('Tu usuario no tiene una empresa habilitada para Carabo.');
    const previousTenant = this.session()?.tenantId;
    const tenantId = businesses.some(business => business.businessCode === previousTenant) ? previousTenant! : businesses[0].businessCode;
    const session: AuthSession = {
      user: response.data.user,
      tenantId,
      roles: isRoot ? ['ROOT_PLATFORM'] : [...(businessRoles[tenantId] ?? [])],
      permissions: this.flattenPermissions(carabo?.permissions ?? []),
      licenseType: response.data.licenseType ?? 'applicative',
      businesses,
      businessRoles,
    };
    localStorage.setItem(TOKEN_KEY, response.data.auth.token);
    this.accessToken.set(response.data.auth.token); this.sessionExpired.set(false);
    this.persistSession(session); this.scheduleExpiry(response.data.auth.token);
  }

  private rolesForBusiness(session: AuthSession, businessCode: string): string[] {
    return session.licenseType === 'root' ? ['ROOT_PLATFORM'] : [...(session.businessRoles[businessCode] ?? [])];
  }
  private flattenPermissions(nodes: PermissionNode[]): string[] {
    return nodes.flatMap(node => [node.elementCode, ...this.flattenPermissions(node.children ?? [])]);
  }
  private persistSession(session: AuthSession): void {
    if (this.isBrowser) localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    this.session.set(session);
  }
  private read(key: string): string | null {
    if (!this.isBrowser) return null;
    try { return localStorage.getItem(key); } catch { return null; }
  }
  private readSession(): AuthSession | null {
    const raw = this.read(SESSION_KEY);
    if (!raw) return null;
    try { const session = JSON.parse(raw) as AuthSession; return session.user && session.tenantId ? session : null; } catch { return null; }
  }
  private scheduleExpiry(token: string | null): void {
    this.clearExpiryTimer();
    const expiresAt = this.readTokenExpiry(token);
    if (!expiresAt) return;
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) { queueMicrotask(() => this.requireReauthentication()); return; }
    const maxDelay = 2_147_000_000;
    this.expiryTimer = setTimeout(() => remaining > maxDelay ? this.scheduleExpiry(this.accessToken()) : this.requireReauthentication(), Math.min(remaining, maxDelay));
  }
  private readTokenExpiry(token: string | null): number | null {
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const decoded = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))) as { exp?: number };
      return typeof decoded.exp === 'number' ? decoded.exp * 1000 : null;
    } catch { return null; }
  }
  private clearExpiryTimer(): void { if (this.expiryTimer !== null) clearTimeout(this.expiryTimer); this.expiryTimer = null; }
}
