import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';

export interface LoginResponse {
  success: boolean;
  username?: string;
  error?: string;
}

/**
 * Håller reda på vem som är inloggad. Sessionen i sig lever i en HttpOnly-cookie
 * som JavaScript inte kan läsa - därför frågar vi backend via `GET /me` i
 * stället för att försöka inspektera något lokalt.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/auth';

  private readonly currentUser = signal<string | null>(null);
  private readonly sessionChecked = signal(false);

  /** Användarnamnet för den inloggade, annars null. */
  readonly user = this.currentUser.asReadonly();

  /** True när `GET /me` har svarat, dvs. när det går att avgöra vad som ska visas. */
  readonly checked = this.sessionChecked.asReadonly();

  /**
   * Frågar backend om det redan finns en giltig session. Anropas vid
   * sidladdning, så att den som redan är inloggad slipper se
   * inloggningsformuläret blinka förbi.
   */
  checkSession(): void {
    this.http
      .get<LoginResponse>(`${this.baseUrl}/me`)
      .pipe(catchError(() => of({ success: false } as LoginResponse)))
      .subscribe((response) => {
        this.currentUser.set(response.success ? (response.username ?? null) : null);
        this.sessionChecked.set(true);
      });
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/login`, { username, password })
      .pipe(tap((response) => this.currentUser.set(response.username ?? null)));
  }

  logout(): Observable<unknown> {
    return this.http
      .post(`${this.baseUrl}/logout`, {})
      .pipe(tap(() => this.currentUser.set(null)));
  }

  /** Anropas av auth-interceptorn när backend svarar 401, t.ex. vid utgången session. */
  markLoggedOut(): void {
    this.currentUser.set(null);
  }
}
