import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService, LoginResponse } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);

  username = '';
  password = '';

  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (!this.username.trim() || !this.password || this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.error.set(null);

    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        // Lösenordet ska inte ligga kvar i komponentens tillstånd efteråt.
        this.password = '';
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.password = '';
        this.error.set(this.describeError(error));
        this.isLoading.set(false);
      },
    });
  }

  /** Backend skickar med ett läsbart fel (fel lösenord respektive låst konto). */
  private describeError(error: HttpErrorResponse): string {
    const body = error.error as LoginResponse | null;
    if (body?.error) {
      return body.error;
    }
    if (error.status === 0) {
      return 'Ingen kontakt med servern. Är backend igång?';
    }
    return 'Inloggningen misslyckades.';
  }
}
