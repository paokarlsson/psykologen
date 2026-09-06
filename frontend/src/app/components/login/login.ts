import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService, LoginResponse } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.Eager,
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

  private describeError(error: HttpErrorResponse): string {
    const body = error.error as LoginResponse | null;
    if (body?.error) {
      return body.error;
    }
    if (error.status === 0) {
      return 'Kunde inte nå tjänsten just nu. Försök igen om en liten stund.';
    }
    return 'Inloggningen misslyckades.';
  }
}
