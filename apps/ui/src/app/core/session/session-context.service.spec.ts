import { SessionContextService } from './session-context.service';
describe('Session scope invalidation', () => {
  it('keeps an immutable snapshot and advances on identity and company changes', () => {
    const context = new SessionContextService();
    context.activate('user-a', 'company-a');
    const first = context.snapshot();
    context.activate('user-a', 'company-b');
    expect(first.businessCode).toBe('company-a');
    expect(Object.isFrozen(first)).toBe(true);
    expect(context.isCurrent(first)).toBe(false);
    context.activate('user-b', 'company-b');
    expect(context.snapshot().userCode).toBe('user-b');
    context.activate();
    expect(context.snapshot().userCode).toBe('');
  });
});
