import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, signal, inject, DestroyRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/session/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly destroyRef = inject(DestroyRef);

  user = '';
  password = '';
  showPassword = false;
  readonly loading = signal(false);
  readonly error = signal('');

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  submit(): void {
    if (!this.user.trim() || !this.password || this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.auth
      .login(this.user, this.password)
      .pipe(finalize(() => this.loading.set(false)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigateByUrl('/'),
        error: (error) => this.error.set(this.accessError(error)),
      });
  }

  private accessError(error: any): string {
    if (error?.status === 0)
      return 'Tordo Auth no responde. Revisa tu conexión y vuelve a intentarlo.';
    if (error?.status === 401)
      return 'Usuario o contraseña incorrectos. Verifica tus datos de Columba e intenta nuevamente.';
    return (
      error?.error?.message ||
      (error instanceof Error ? error.message : '') ||
      'No pudimos completar el acceso. Inténtalo nuevamente o solicita a un administrador que revise tu rol Carabo.'
    );
  }
}
