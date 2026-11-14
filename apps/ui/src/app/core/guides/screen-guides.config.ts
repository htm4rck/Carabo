import { inject, Provider } from '@angular/core';
import { SCREEN_GUIDES_CONFIG, ScreenGuidesConfig } from '@tordo-io/assistant-ui/guides';
import { AuthService } from '../session/auth.service';
import { SessionContextService } from '../session/session-context.service';
import { SCREEN_GUIDES } from './screen-guides.generated';

export function screenGuidesConfig(): ScreenGuidesConfig {
  const auth = inject(AuthService);
  const context = inject(SessionContextService);
  return {
    guides: SCREEN_GUIDES,
    changes: context.changes$,
    snapshot: () => {
      const token = auth.accessToken(),
        businessCode = auth.session()?.tenantId;
      return token && businessCode
        ? {
            businessCode,
            identityKey: JSON.stringify([context.snapshot(), token]),
            headers: { Authorization: 'Bearer ' + token },
          }
        : null;
    },
  };
}
export const SCREEN_GUIDES_PROVIDER: Provider = {
  provide: SCREEN_GUIDES_CONFIG,
  useFactory: screenGuidesConfig,
};
