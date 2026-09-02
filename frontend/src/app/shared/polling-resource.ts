import { DestroyRef, Signal, inject, signal } from '@angular/core';
import { Observable, Subject, interval, startWith, switchMap } from 'rxjs';

export interface PollingResource<T> {
  /** Senast hämtade värde. */
  readonly value: Signal<T>;
  /** True medan en hämtning pågår. */
  readonly isLoading: Signal<boolean>;
  /** Tidpunkt för senast lyckade hämtning, om någon. */
  readonly lastUpdated: Signal<Date | undefined>;
  /** Hämta direkt, utan att vänta på nästa polling-intervall. */
  refresh(): void;
}

/**
 * Kör `fetch()` direkt och sedan upprepat var `intervalMs` millisekund, tills
 * komponenten som skapade resursen förstörs. Tänkt att anropas i ett fält-
 * initierare (injection context), t.ex. `private readonly x = pollingResource(...)`.
 *
 * Ersätter den upprepade ngOnInit/ngOnDestroy/interval/Subscription-koden som
 * annars behövs i varje komponent som pollar backend.
 */
export function pollingResource<T>(
  fetch: () => Observable<T>,
  initialValue: T,
  intervalMs = 5000,
): PollingResource<T> {
  const destroyRef = inject(DestroyRef);

  const value = signal(initialValue);
  const isLoading = signal(false);
  const lastUpdated = signal<Date | undefined>(undefined);
  const trigger = new Subject<void>();

  const subscription = trigger
    .pipe(
      startWith(undefined),
      switchMap(() => {
        isLoading.set(true);
        return fetch();
      }),
    )
    .subscribe({
      next: (result) => {
        value.set(result);
        lastUpdated.set(new Date());
        isLoading.set(false);
      },
      error: (error) => {
        console.error('Polling misslyckades:', error);
        isLoading.set(false);
      },
    });

  const intervalSubscription = interval(intervalMs).subscribe(() => trigger.next());

  destroyRef.onDestroy(() => {
    subscription.unsubscribe();
    intervalSubscription.unsubscribe();
  });

  return {
    value: value.asReadonly(),
    isLoading: isLoading.asReadonly(),
    lastUpdated: lastUpdated.asReadonly(),
    refresh: () => trigger.next(),
  };
}
