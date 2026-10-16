import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="login-page">
      <section class="login-story" aria-label="Carabo Facturación Electrónica">
        <div class="brand-lockup">
          <img src="assets/images/logo.png" alt="Carabo, facturación electrónica">
        </div>
        <div class="story-copy">
          <div class="product-name"><strong>CARABO</strong><span>Facturación electrónica</span></div>
          <h1>Control fiscal que avanza contigo.</h1>
          <p>Cada comprobante mantiene una ruta visible desde la emisión hasta la respuesta de SUNAT.</p>
        </div>
        <div class="fiscal-path" aria-hidden="true">
          <div class="path-step complete"><i></i><span>Emisión</span></div>
          <div class="path-line complete"></div>
          <div class="path-step complete"><i></i><span>Firma</span></div>
          <div class="path-line active"></div>
          <div class="path-step active"><i></i><span>SUNAT</span></div>
          <div class="path-line"></div>
          <div class="path-step"><i></i><span>CDR</span></div>
        </div>
      </section>

      <section class="login-panel">
        <form (ngSubmit)="submit()" class="login-form">
          <div class="mobile-brand"><img src="assets/images/icono.png" alt=""><span><strong>CARABO</strong><small>Facturación electrónica</small></span></div>
          <div class="mobile-story">
            <p>Control fiscal desde la emisión hasta la respuesta de SUNAT.</p>
            <div class="mobile-path" aria-label="Ruta fiscal: emisión, firma, SUNAT y CDR">
              <span>Emisión</span><i></i><span>Firma</span><i></i><span>SUNAT</span><i></i><span>CDR</span>
            </div>
          </div>
          <h2>Ingresa a tu operación</h2>
          <p>Usa las mismas credenciales con las que accedes a Columba.</p>
          <p class="access-note"><i class="ri-shield-check-line"></i> Tu acceso y permisos se validan con Tordo.</p>

          <label for="user">Usuario o correo</label>
          <input id="user" name="user" type="text" autocomplete="username" [(ngModel)]="user" required autofocus>

          <label for="password">Contraseña</label>
          <div class="password-field">
            <input id="password" name="password" [type]="showPassword ? 'text' : 'password'" autocomplete="current-password" [(ngModel)]="password" required>
            <button type="button" class="reveal" (click)="showPassword = !showPassword" [attr.aria-label]="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'">
              <i [class]="showPassword ? 'ri-eye-off-line' : 'ri-eye-line'"></i>
            </button>
          </div>

          @if (error()) { <div class="login-error" role="alert"><i class="ri-error-warning-line"></i>{{ error() }}</div> }

          <button class="continue-button" type="submit" [disabled]="loading() || !user.trim() || !password">
            <span>{{ loading() ? 'Validando acceso' : 'Continuar a Carabo' }}</span>
            <i [class]="loading() ? 'ri-loader-4-line spin' : 'ri-arrow-right-line'"></i>
          </button>
          <small>La sesión y los permisos son administrados por Tordo Auth.</small>
        </form>
      </section>
    </main>
  `,
  styles: [`
    :host{display:block;min-height:100vh}.login-page{min-height:100vh;display:grid;grid-template-columns:minmax(420px,1.1fr) minmax(420px,.9fr);background:#fff}.login-story{position:relative;overflow:hidden;background:#173317;color:#fff;padding:42px 64px 56px;display:flex;flex-direction:column;justify-content:space-between}.login-story:after{content:'';position:absolute;width:520px;height:520px;border:1px solid rgba(128,239,128,.2);border-radius:50%;right:-190px;top:-160px;box-shadow:0 0 0 80px rgba(128,239,128,.035),0 0 0 160px rgba(128,239,128,.025)}.brand-lockup{position:relative;z-index:1;width:clamp(340px,34vw,620px);padding:10px 18px 13px;background:#fbfaf4;border-radius:10px;box-shadow:0 18px 42px rgba(3,15,5,.26)}.brand-lockup img{display:block;width:100%;height:auto}.story-copy{max-width:640px;position:relative;z-index:1}.product-name{display:block;color:#80ef80;letter-spacing:.22em;font-size:13px;font-weight:800;margin-bottom:24px}.story-copy h1{font-size:clamp(42px,5vw,72px);line-height:1.02;letter-spacing:-.04em;margin:0 0 24px;max-width:720px}.story-copy p{font-size:18px;line-height:1.6;color:#cfe0d2;max-width:580px}.fiscal-path{display:flex;align-items:flex-start;position:relative;z-index:1;max-width:620px}.path-step{width:76px;text-align:center;color:#769479;font-size:12px}.path-step i{display:block;width:14px;height:14px;border:3px solid #768a76;border-radius:50%;background:#173317;margin:0 auto 9px}.path-step.complete,.path-step.active{color:#fff}.path-step.complete i{background:#80ef80;border-color:#80ef80}.path-step.active i{border-color:#80ef80;box-shadow:0 0 0 6px rgba(128,239,128,.12)}.path-line{height:2px;flex:1;background:#49634c;margin-top:6px}.path-line.complete,.path-line.active{background:#80ef80}.login-panel{display:grid;place-items:center;padding:48px}.login-form{width:min(100%,430px)}.mobile-brand{display:none}.secure-label{display:inline-flex;align-items:center;gap:7px;color:#426845;background:#f0f8f1;padding:7px 10px;border-radius:6px;font-size:12px;font-weight:700}.login-form h2{font-size:34px;letter-spacing:-.035em;margin:22px 0 9px;color:#173317}.login-form>p{color:#657468;line-height:1.55;margin-bottom:32px}.login-form label{display:block;font-weight:700;color:#27462b;margin:18px 0 8px}.login-form input{width:100%;height:50px;border:1px solid #cbd8cd;border-radius:7px;padding:0 14px;font:inherit;color:#173317;outline:none;transition:border-color .18s,box-shadow .18s}.login-form input:focus{border-color:#8cbd8c;box-shadow:0 0 0 4px rgba(140,189,140,.18)}.password-field{position:relative}.password-field input{padding-right:48px}.reveal{position:absolute;right:8px;top:7px;width:36px;height:36px;border:0;background:transparent;color:#506b53;cursor:pointer;font-size:19px}.login-error{display:flex;gap:9px;align-items:center;color:#a12f2f;background:#fff1f1;border:1px solid #f1c9c9;padding:11px 13px;border-radius:7px;margin-top:18px}.continue-button{width:100%;height:52px;margin-top:24px;border:0;border-radius:7px;background:#173317;color:white;font:700 15px Lato,sans-serif;display:flex;align-items:center;justify-content:space-between;padding:0 20px;cursor:pointer;transition:transform .18s,background .18s,box-shadow .18s}.continue-button:not(:disabled):hover{background:#234b29;transform:translateY(-1px);box-shadow:0 10px 24px rgba(23,51,23,.2)}.continue-button:not(:disabled):hover i{transform:translateX(4px)}.continue-button:disabled{opacity:.55;cursor:not-allowed}.continue-button i{font-size:20px;transition:transform .18s}.login-form small{display:block;text-align:center;color:#7c897e;margin-top:18px}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:850px){.login-page{grid-template-columns:1fr}.login-story{min-height:280px;padding:28px 32px 32px}.brand-lockup{width:172px;padding:6px 10px 7px}.story-copy h1{font-size:38px}.story-copy p{font-size:15px}.fiscal-path{display:none}.login-panel{padding:38px 24px}}@media(max-width:520px){.login-story{display:none}.login-panel{min-height:100vh}.mobile-brand{display:flex;align-items:center;gap:10px;color:#173317;font-weight:900;margin-bottom:42px}.mobile-brand img{width:52px;height:52px;object-fit:contain;filter:drop-shadow(0 7px 12px rgba(23,51,23,.18))}.login-form h2{font-size:30px}}
    .product-name{display:flex;align-items:baseline;gap:13px;letter-spacing:0}.product-name strong{letter-spacing:.22em}.product-name span{padding-left:13px;border-left:1px solid rgba(128,239,128,.4);color:#cfe0d2;font-size:12px;font-weight:400}.mobile-story{display:none}.access-note{display:flex!important;align-items:center;gap:7px;color:#426845!important;background:#f0f8f1;padding:9px 11px;border-radius:6px;font-size:12px;font-weight:700;margin-bottom:0!important}.login-error{line-height:1.4}.mobile-brand strong,.mobile-brand small{display:block}.mobile-brand small{letter-spacing:0}
    @media(max-width:850px) and (min-width:521px){.fiscal-path{display:flex;margin-top:34px}}
    @media(max-width:520px){.login-panel{place-items:start center;padding-top:34px}.mobile-brand{letter-spacing:0;margin-bottom:22px}.mobile-brand strong{font-size:13px;letter-spacing:.16em}.mobile-brand small{margin-top:3px;text-align:left;color:#657468;font-size:10px}.mobile-story{display:block;margin:0 0 24px;padding:16px;background:#173317;border-radius:8px;color:#fff}.mobile-story p{margin:0 0 13px;font-size:13px;line-height:1.45;color:#d5e3d6}.mobile-path{display:flex;align-items:center;gap:6px;color:#fff;font-size:9px}.mobile-path i{height:2px;flex:1;background:#80ef80}}
  `]
})
export class LoginComponent {
  user = '';
  password = '';
  showPassword = false;
  readonly loading = signal(false);
  readonly error = signal('');

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  submit(): void {
    if (!this.user.trim() || !this.password || this.loading()) return;
    this.loading.set(true); this.error.set('');
    this.auth.login(this.user, this.password).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: error => this.error.set(this.accessError(error)),
    });
  }

  private accessError(error: any): string {
    if (error?.status === 0) return 'Tordo Auth no responde. Revisa tu conexión y vuelve a intentarlo.';
    if (error?.status === 401) return 'Usuario o contraseña incorrectos. Verifica tus datos de Columba e intenta nuevamente.';
    return error?.error?.message || 'No pudimos completar el acceso. Inténtalo nuevamente o solicita a un administrador que revise tu rol Carabo.';
  }
}
