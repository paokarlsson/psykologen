import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';

export interface LoginResponse {
  success: boolean;
  username?: string;
  error?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/auth';

  private readonly currentUser = signal<string | null>(null);
  private readonly sessionChecked = signal(false);

  readonly user = this.currentUser.asReadonly();

  readonly checked = this.sessionChecked.asReadonly();

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

  markLoggedOut(): void {
    this.currentUser.set(null);
  }
}
