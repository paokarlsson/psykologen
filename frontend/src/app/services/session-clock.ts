import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

/**
 * Speglar backendens sessionsklocka. Klockan startar när sessionen skapas i
 * backend, inte när sidan laddas, så nedräkningen måste komma därifrån -
 * annars blir den fel efter en omladdning. Mellan svaren tickar den lokalt så
 * att siffran inte står still.
 */
@Injectable({ providedIn: 'root' })
export class SessionClock {
  private readonly elapsedAtSync = signal<number | null>(null);
  private readonly durationMinutes = signal(0);
  private readonly syncedAt = signal(0);
  private readonly now = signal(Date.now());

  private readonly complete = signal(false);

  /** Sant först när samtalet är igång - före det finns ingen tid att räkna ned. */
  readonly started = computed(() => this.elapsedAtSync() !== null);

  readonly remainingMinutes = computed(() => {
    const elapsed = this.elapsedAtSync();
    if (elapsed === null) {
      return null;
    }
    const driftMinutes = (this.now() - this.syncedAt()) / 60000;
    return Math.max(0, this.durationMinutes() - elapsed - driftMinutes);
  });

  readonly label = computed(() => {
    if (this.complete()) {
      return 'Sessionen är avslutad';
    }
    const remaining = this.remainingMinutes();
    if (remaining === null) {
      return '';
    }
    if (remaining <= 0) {
      return 'Tiden är ute';
    }
    if (remaining < 1) {
      return 'Mindre än en minut kvar';
    }
    return `${Math.round(remaining)} min kvar`;
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 10_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  /** Anropas med svaren från /start, /message och /conversation. */
  sync(elapsedMinutes?: number, sessionDurationMinutes?: number): void {
    if (elapsedMinutes === undefined || sessionDurationMinutes === undefined) {
      return;
    }
    this.elapsedAtSync.set(elapsedMinutes);
    this.durationMinutes.set(sessionDurationMinutes);
    this.syncedAt.set(Date.now());
    this.now.set(Date.now());
  }

  markComplete(): void {
    this.complete.set(true);
  }
}
