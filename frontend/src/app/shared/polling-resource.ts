import { DestroyRef, Signal, effect, inject, signal } from '@angular/core';
import { Observable, Subject, interval, switchMap } from 'rxjs';

export interface PollingResource<T> {
  readonly value: Signal<T>;
  readonly isLoading: Signal<boolean>;
  readonly lastUpdated: Signal<Date | undefined>;
  refresh(): void;
}

export interface PollingOptions {
  intervalMs?: number;
  /**
   * Pollningen vilar medan den är false och hämtar direkt när den slår om till
   * true, så att det man fäller ut är aktuellt utan att ha kostat anrop under
   * tiden det var dolt.
   */
  enabled?: Signal<boolean>;
}

/** Anropas i en fältinitierare (injection context), inte i en metod - använder `inject()`. */
export function pollingResource<T>(
  fetch: () => Observable<T>,
  initialValue: T,
  options: PollingOptions = {},
): PollingResource<T> {
  const { intervalMs = 5000, enabled } = options;
  const destroyRef = inject(DestroyRef);

  const value = signal(initialValue);
  const isLoading = signal(false);
  const lastUpdated = signal<Date | undefined>(undefined);
  const trigger = new Subject<void>();

  const subscription = trigger
    .pipe(
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

  const intervalSubscription = interval(intervalMs).subscribe(() => {
    if (!enabled || enabled()) {
      trigger.next();
    }
  });

  // Första hämtningen, och en ny så fort resursen blir synlig igen. Ersätter
  // det startWith som förut hämtade oavsett om någon tittade.
  let wasEnabled = false;
  effect(() => {
    const isEnabled = enabled ? enabled() : true;
    if (isEnabled && !wasEnabled) {
      queueMicrotask(() => trigger.next());
    }
    wasEnabled = isEnabled;
  });

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
