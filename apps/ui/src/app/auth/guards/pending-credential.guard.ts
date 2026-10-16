import { CanDeactivateFn } from '@angular/router';

export interface PendingCredentialAware {
  canLeaveWithCredential(): boolean;
}

export const pendingCredentialGuard: CanDeactivateFn<PendingCredentialAware> = component =>
  component.canLeaveWithCredential();
