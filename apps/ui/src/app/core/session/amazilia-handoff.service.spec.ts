import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AmaziliaHandoffService } from './amazilia-handoff.service';
import { amaziliaHandoff } from './amazilia-handoff';
import { AuthService, AuthSession } from './auth.service';
describe('Included Amazilia context in Carabo', () => {
  const input = {
    businessCode: 'B20123456789',
    userCode: 'U1',
    branchCode: 'STORE1',
    returnTo: 'support',
    task: 'settings',
  };
  const current: AuthSession = {
    user: { userCode: 'U1', firstName: 'Test', lastName: 'User', email: 'fixture@example.invalid' },
    tenantId: input.businessCode,
    roles: [],
    permissions: [],
    licenseType: 'applicative',
    businesses: [],
    businessRoles: {},
  };
  const session = signal<AuthSession | null>(current);
  let allow = true,
    service: AmaziliaHandoffService;
  beforeEach(() => {
    sessionStorage.clear();
    allow = true;
    session.set(current);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { session, selectBusiness: () => allow } }],
    });
    service = TestBed.inject(AmaziliaHandoffService);
  });
  afterEach(() => sessionStorage.clear());
  it('binds the company, account, store and fixed return point', () => {
    expect(service.remember(input)).toBe(true);
    expect(service.loginDestination()).toBe('/companion/amazilia');
    expect(service.destination()).toBe('/documents');
    expect(service.active()?.branchCode).toBe('STORE1');
    expect(service.returnUrl()).toBe('/amazilia/ui/#/pos/support');
  });
  it('denies another account or a company outside authoritative discovery', () => {
    service.remember(input);
    session.set({ ...current, user: { ...current.user, userCode: 'U2' } });
    expect(service.destination()).toBeNull();
    expect(service.active()).toBeNull();
    session.set(current);
    allow = false;
    expect(service.destination()).toBeNull();
    session.set({ ...current, tenantId: 'B20999999990' });
    expect(service.active()).toBeNull();
  });
  it('rejects untrusted URLs, malformed identities and array values', () => {
    for (const patch of [
      { returnTo: 'https://foreign.invalid' },
      { userCode: '<script>' },
      { returnTo: ['support'] },
    ])
      expect(amaziliaHandoff({ ...input, ...patch })).toBeNull();
    expect(service.remember({ ...input, returnTo: 'https://foreign.invalid' })).toBe(false);
    expect(service.loginDestination()).toBe('/');
  });
  it('preserves BirdPort company, store and fixed return destination through included application login', () => {
    expect(service.remember({ ...input, application: 'birdport' })).toBe(true);
    expect(service.loginDestination()).toBe('/companion/birdport');
    expect(service.returnUrl()).toBe('/birdport/admin/product?branch=STORE1');
    expect(amaziliaHandoff({ ...input, application: 'https://foreign.invalid' })).toBeNull();
    expect(amaziliaHandoff({ ...input, application: ['birdport'] })).toBeNull();
  });
});
