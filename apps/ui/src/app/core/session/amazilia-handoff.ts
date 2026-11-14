export interface AmaziliaHandoff {
  application?: 'birdport';
  businessCode: string;
  userCode: string;
  branchCode: string;
  returnTo: 'support' | 'pos';
  task: 'settings' | 'prices';
}
export function amaziliaHandoff(value: unknown): AmaziliaHandoff | null {
  if (!value || typeof value !== 'object') return null;
  const data = value as Record<string, unknown>;
  if (
    (data['application'] !== undefined && data['application'] !== 'birdport') ||
    typeof data['businessCode'] !== 'string' ||
    !/^B\d{11}$/.test(data['businessCode']) ||
    typeof data['userCode'] !== 'string' ||
    !/^[A-Za-z0-9:_-]{1,100}$/.test(data['userCode']) ||
    typeof data['branchCode'] !== 'string' ||
    !/^[A-Za-z0-9:_-]{0,120}$/.test(data['branchCode']) ||
    typeof data['returnTo'] !== 'string' ||
    !['support', 'pos'].includes(data['returnTo']) ||
    typeof data['task'] !== 'string' ||
    !['settings', 'prices'].includes(data['task'])
  )
    return null;
  return {
    ...(data['application'] === 'birdport' ? { application: 'birdport' as const } : {}),
    businessCode: data['businessCode'],
    userCode: data['userCode'],
    branchCode: data['branchCode'],
    returnTo: data['returnTo'] as AmaziliaHandoff['returnTo'],
    task: data['task'] as AmaziliaHandoff['task'],
  };
}
export function amaziliaReturn(context: AmaziliaHandoff): string {
  if (context.application === 'birdport')
    return '/birdport/admin/product?branch=' + encodeURIComponent(context.branchCode);
  return context.returnTo === 'support' ? '/amazilia/ui/#/pos/support' : '/amazilia/ui/#/homePage';
}
