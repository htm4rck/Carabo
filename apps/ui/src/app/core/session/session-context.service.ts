import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
export interface SessionContext {
  readonly revision: number;
  readonly userCode: string;
  readonly businessCode: string;
  readonly environment: string;
}
/** Invalidates observations synchronously when identity or scope changes. */
@Injectable({ providedIn: 'root' })
export class SessionContextService {
  private readonly state = new BehaviorSubject<SessionContext>(
    Object.freeze({ revision: 0, userCode: '', businessCode: '', environment: '' }),
  );
  readonly changes$ = this.state.asObservable();
  snapshot(): SessionContext {
    return this.state.value;
  }
  activate(userCode = '', businessCode = '', environment = ''): void {
    this.state.next(
      Object.freeze({
        revision: this.state.value.revision + 1,
        userCode,
        businessCode,
        environment,
      }),
    );
  }
  isCurrent(snapshot: SessionContext): boolean {
    return snapshot.revision === this.state.value.revision;
  }
}
