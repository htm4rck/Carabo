import { vi } from 'vitest';
import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../session/auth.service';
import { SessionContextService } from '../session/session-context.service';
import { environment } from '../../../environments/environment';
import { apiKeyInterceptor } from './api-key.interceptor';
describe('Credential boundary and context lifetime', () => {
  let http: HttpClient,
    requests: HttpTestingController,
    context: SessionContextService,
    token: string,
    logout: ReturnType<typeof vi.fn>;
  const api = environment.apiUrl + '/documents';
  beforeEach(() => {
    token = 'synthetic-current-token';
    logout = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr(), withInterceptors([apiKeyInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            accessToken: () => token,
            session: () => ({ tenantId: 'B11111111111' }),
            requireReauthentication: logout,
          },
        },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    context = TestBed.inject(SessionContextService);
    context.activate('user', 'company-a');
  });
  afterEach(() => requests.verify());
  it('sends the credential to its configured API', () => {
    http.get(api).subscribe();
    const request = requests.expectOne(api);
    expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-current-token');
    request.flush({});
  });
  for (const url of ['https://external.invalid/api/private', '/assets/build-info.json'])
    it('does not attach credentials or expire on an excluded destination ' + url, () => {
      http.get(url).subscribe({ error: () => undefined });
      const request = requests.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({}, { status: 401, statusText: 'Unauthorized' });
      expect(logout).not.toHaveBeenCalled();
    });
  it('cancels observation of an old company response', () => {
    let received = false;
    http.get(api).subscribe(() => {
      received = true;
    });
    const request = requests.expectOne(api);
    context.activate('user', 'company-b');
    expect(request.cancelled).toBe(true);
    expect(received).toBe(false);
  });
  it('invalidates a current unauthorized session', () => {
    http.get(api).subscribe({ error: () => undefined });
    requests.expectOne(api).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(logout).toHaveBeenCalled();
  });
  it('does not invalidate a replacement credential after an old 401', () => {
    http.get(api).subscribe({ error: () => undefined });
    const request = requests.expectOne(api);
    token = 'replacement-token';
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(logout).not.toHaveBeenCalled();
  });
  it('allows an initiated write to finish while discarding its old-context result', () => {
    let received = false;
    http.post(api, {}).subscribe(() => {
      received = true;
    });
    const request = requests.expectOne(api);
    context.activate('user', 'company-b');
    expect(request.cancelled).toBe(false);
    request.flush({ committed: true });
    expect(received).toBe(false);
  });
});
