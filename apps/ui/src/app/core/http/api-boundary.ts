import { environment } from '../../../environments/environment';
export function isCaraboApi(
  url: string,
  origin = typeof location === 'undefined' ? 'https://unavailable.invalid' : location.origin,
): boolean {
  const target = new URL(url, origin),
    base = new URL(environment.apiUrl, origin);
  return (
    target.origin === base.origin &&
    (target.pathname === base.pathname.replace(/\/$/, '') ||
      target.pathname.startsWith(base.pathname.replace(/\/$/, '') + '/'))
  );
}
