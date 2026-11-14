import { describe, it, expect } from 'vitest';
import { Injector, runInInjectionContext } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../session/auth.service';
import { SessionContextService } from '../session/session-context.service';
import { screenGuidesConfig } from './screen-guides.config';

describe('screen guide session scope', () => {
  function fixture() {
    let token = 'user-session',
      businessCode = 'B20123456789',
      revision = 1;
    const auth = { accessToken: () => token, session: () => ({ tenantId: businessCode }) };
    const injector = Injector.create({
      providers: [
        { provide: AuthService, useValue: auth },
        {
          provide: SessionContextService,
          useValue: { changes$: new BehaviorSubject(0), snapshot: () => ({ revision }) },
        },
      ],
    });
    const config = runInInjectionContext(injector, screenGuidesConfig);
    return {
      config,
      company: (value: string) => {
        businessCode = value;
        revision++;
      },
      actor: () => {
        token = 'another-session';

        revision++;
      },
      logout: () => {
        token = '';
      },
    };
  }
  it('uses the selected company and invalidates A to B to A and a different user', () => {
    const f = fixture(),
      first = f.config.snapshot();
    expect(first?.businessCode).toBe('B20123456789');
    f.company('B20999999990');
    expect(f.config.snapshot()?.identityKey).not.toEqual(first?.identityKey);
    f.company('B20123456789');
    expect(f.config.snapshot()?.identityKey).not.toEqual(first?.identityKey);
    const second = f.config.snapshot();
    f.actor();
    expect(f.config.snapshot()?.identityKey).not.toEqual(second?.identityKey);
  });
  it('requires a selected company and an authenticated user', () => {
    const f = fixture();
    f.company('');
    expect(f.config.snapshot()).toBeNull();
    f.company('B20123456789');
    f.logout();
    expect(f.config.snapshot()).toBeNull();
  });
});
