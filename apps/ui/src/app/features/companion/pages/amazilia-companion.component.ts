import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/session/auth.service';
import { AmaziliaHandoffService } from '../../../core/session/amazilia-handoff.service';
@Component({
  selector: 'app-amazilia-companion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main style="max-width:40rem;margin:3rem auto;padding:1rem">
    <h1>
      Carabo incluido con
      {{ handoff.context()?.application === 'birdport' ? 'BirdPort' : 'Amazilia' }}
    </h1>
    <p role="status">{{ message() }}</p>
    @if (relogin()) {
      <button type="button" (click)="login()">Iniciar sesión con la cuenta de origen</button>
    }
    <a
      [href]="
        handoff.context()?.application === 'birdport'
          ? '/birdport/admin/product'
          : '/amazilia/ui/#/pos/support'
      "
      >Volver a {{ handoff.context()?.application === 'birdport' ? 'BirdPort' : 'Amazilia' }}</a
    >
  </main>`,
})
export class AmaziliaCompanionComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly handoff = inject(AmaziliaHandoffService);
  readonly message = signal('Comprobando la cuenta y empresa solicitadas…');
  readonly relogin = signal(false);
  constructor() {
    const query = inject(ActivatedRoute).snapshot.queryParamMap;
    if (
      query.has('businessCode') &&
      !this.handoff.remember({
        ...(query.get('application') ? { application: query.get('application') } : {}),
        businessCode: query.get('businessCode'),
        userCode: query.get('userCode'),
        branchCode: query.get('branchCode') ?? '',
        returnTo: query.get('returnTo') ?? 'support',
        task: 'settings',
      })
    ) {
      this.message.set(
        'No se pudo conservar un contexto válido. Abre Carabo de nuevo desde la aplicación de origen.',
      );
      return;
    }
    if (!this.auth.isAuthenticated()) {
      void this.router.navigateByUrl('/login');
      return;
    }
    this.auth
      .refreshBusinesses()
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe({
        next: () => {
          const destination = this.handoff.destination();
          if (destination) {
            void this.router.navigateByUrl(destination);
          } else {
            this.message.set(
              'La cuenta activa o sus empresas no coinciden con la solicitud de la aplicación de origen.',
            );
            this.relogin.set(true);
          }
        },
        error: () =>
          this.message.set(
            'No se pudieron comprobar las empresas autorizadas. Vuelve a abrir Carabo desde la aplicación de origen.',
          ),
      });
  }
  login(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }
}
