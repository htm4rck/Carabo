import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';

export interface AuthUser { userCode: string; firstName: string; lastName: string; email: string; }
export interface UserBusiness { businessCode: string; defaultRoleCode: string; isActive: boolean; }
export interface LoginResponse { success: boolean; data: { auth: { token: string }; user: AuthUser; businesses: UserBusiness[] }; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenKey = 'carabo_token';
  private userKey = 'carabo_user';
  private businessesKey = 'carabo_businesses';
  private selectedBusinessKey = 'carabo_selected_business';
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private currentUser$ = new BehaviorSubject<AuthUser | null>(this.loadUser());
  private selectedBusiness$ = new BehaviorSubject<string | null>(this.loadSelectedBusiness());

  user$ = this.currentUser$.asObservable();
  business$ = this.selectedBusiness$.asObservable();

  constructor(private http: HttpClient) {}

  login(user: string, password: string, signature: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      '/auth/login', { user, password },
      { headers: new HttpHeaders({ signature }) }
    ).pipe(tap(res => {
      if (res.success && this.isBrowser) {
        localStorage.setItem(this.tokenKey, res.data.auth.token);
        localStorage.setItem(this.userKey, JSON.stringify(res.data.user));
        localStorage.setItem(this.businessesKey, JSON.stringify(res.data.businesses));
        this.currentUser$.next(res.data.user);
        if (res.data.businesses.length > 0) this.selectBusiness(res.data.businesses[0].businessCode);
      }
    }));
  }

  logout(): void {
    if (!this.isBrowser) return;
    [this.tokenKey, this.userKey, this.businessesKey, this.selectedBusinessKey].forEach(k => localStorage.removeItem(k));
    this.currentUser$.next(null);
    this.selectedBusiness$.next(null);
  }

  getToken(): string | null { return this.isBrowser ? localStorage.getItem(this.tokenKey) : null; }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.exp && payload.exp * 1000 < Date.now()) { this.logout(); return false; }
      return true;
    } catch { this.logout(); return false; }
  }

  getBusinesses(): UserBusiness[] {
    if (!this.isBrowser) return [];
    const raw = localStorage.getItem(this.businessesKey);
    return raw ? JSON.parse(raw) : [];
  }

  selectBusiness(code: string): void { if (this.isBrowser) localStorage.setItem(this.selectedBusinessKey, code); this.selectedBusiness$.next(code); }
  getSelectedBusiness(): string | null { return this.isBrowser ? localStorage.getItem(this.selectedBusinessKey) : null; }

  private loadUser(): AuthUser | null {
    if (!this.isBrowser) return null;
    const raw = localStorage.getItem(this.userKey);
    return raw ? JSON.parse(raw) : null;
  }

  private loadSelectedBusiness(): string | null { return this.isBrowser ? localStorage.getItem(this.selectedBusinessKey) : null; }
}
