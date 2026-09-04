import { DestroyRef, Signal, inject, signal } from '@angular/core';
import { Observable, Subject, interval, startWith, switchMap } from 'rxjs';

export interface PollingResource<T> {
  readonly value: Signal<T>;
  readonly isLoading: Signal<boolean>;
  readonly lastUpdated: Signal<Date | undefined>;
  refresh(): void;
}

/** Anropas i en fältinitierare (injection context), inte i en metod - använder `inject()`. */
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
