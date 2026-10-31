import { DOCUMENT, PlatformLocation } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { appConfig } from './app.config';

describe('Tordo browser session CSRF', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  const proof = 'c'.repeat(43);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: PlatformLocation, useValue: { href: 'https://dev.tordo.io/carabo/ui/' } },
      ],
    });
    vi.spyOn(TestBed.inject(DOCUMENT), 'cookie', 'get').mockReturnValue(
      '__Host-tordo-xsrf=' + proof,
    );
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    requests.verify();
    vi.restoreAllMocks();
  });

  for (const path of ['/auth/login', '/auth/logout', '/carabo/api/documents']) {
    it('sends the existing Tordo session proof when posting to ' + path, () => {
      http.post(path, {}).subscribe();
      const request = requests.expectOne(path);
      expect(request.request.headers.get('x-tordo-csrf')).toBe(proof);
      request.flush({});
    });
  }

  for (const url of ['https://external.invalid/auth/login', '//external.invalid/auth/login']) {
    it('keeps the Tordo session proof away from ' + url, () => {
      http.post(url, {}).subscribe();
      const request = requests.expectOne(url);
      expect(request.request.headers.has('x-tordo-csrf')).toBe(false);
      request.flush({});
    });
  }

  it('does not send a session proof on a read', () => {
    http.get('/auth/session').subscribe();
    const request = requests.expectOne('/auth/session');
    expect(request.request.headers.has('x-tordo-csrf')).toBe(false);
    request.flush({});
  });
});
