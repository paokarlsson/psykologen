import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

/**
 * Fångar 401 från backend och markerar användaren som utloggad.
 *
 * Det är det som gör att en utgången session tar en tillbaka till
 * inloggningen i stället för att appen tyst slutar fungera - även när det är
 * bakgrundspollningen av profil/plan/historik som upptäcker det först.
 *
 * Felet skickas vidare oförändrat, så komponenten som gjorde anropet får
 * fortfarande hantera det som vanligt.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.markLoggedOut();
      }
      return throwError(() => error);
    }),
  );
};
