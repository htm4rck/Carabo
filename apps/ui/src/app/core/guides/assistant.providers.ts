import { ASSISTANT_CONFIG, manualAssistantConfig } from '@tordo-io/assistant-ui';
import { OPERATIONAL_MANUALS } from './operational-manuals.generated';
import { SCREEN_GUIDES } from './screen-guides.generated';

export const ASSISTANT_PROVIDER = {
  provide: ASSISTANT_CONFIG,
  useFactory: () => {
    return manualAssistantConfig({
      application: 'carabo',
      label: 'Carabo',
      manualRoot: '/manuals',
      fullManualId: 'carabo-manual-usuario',
      manuals: OPERATIONAL_MANUALS,
      guides: SCREEN_GUIDES,
      cookieSession: false,
    });
  },
};
