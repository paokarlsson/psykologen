import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

/**
 * Fälten backend lägger på varje svar som rör samtalet. Valfria: ett anrop mot
 * en äldre backend saknar dem.
 */
export interface SessionMeterFields {
  elapsedMinutes?: number;
  sessionDurationMinutes?: number;
  costUsd?: number;
  costComplete?: boolean;
}

/**
 * Delas av glaslådan och skalet, så att beloppen aldrig kan skilja sig åt.
 * "minst" när något anrop kördes på en modell utan pris i prislistan - summan
 * är då för låg, inte okänd.
 */
export function formatTotalCost(costUsd: number, complete: boolean): string {
  const belopp = '$' + costUsd.toFixed(4);
  return complete ? belopp : 'minst ' + belopp;
}

/**
 * Sessionens mätare: hur lång tid som är kvar och vad anropen kostat hittills.
 * Båda kommer från backend - klockan startar när sessionen skapas där, inte
 * när sidan laddas, och kostnaden räknas på anropen som faktiskt gjorts.
 */
@Injectable({ providedIn: 'root' })
export class SessionMeter {
  private readonly elapsedAtSync = signal<number | null>(null);
  private readonly durationMinutes = signal(0);
  private readonly syncedAt = signal(0);
  private readonly now = signal(Date.now());

  private readonly costUsd = signal<number | null>(null);
  private readonly costComplete = signal(true);
  private readonly complete = signal(false);

  /** Sant först när samtalet är igång - före det finns inget att mäta. */
  readonly started = computed(() => this.elapsedAtSync() !== null);

  readonly remainingMinutes = computed(() => {
    const elapsed = this.elapsedAtSync();
    if (elapsed === null) {
      return null;
    }
    const driftMinutes = (this.now() - this.syncedAt()) / 60000;
    return Math.max(0, this.durationMinutes() - elapsed - driftMinutes);
  });

  readonly timeLabel = computed(() => {
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

  readonly costLabel = computed(() => {
    const cost = this.costUsd();
    return cost === null ? '' : formatTotalCost(cost, this.costComplete());
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 10_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  /** Anropas med svaren från /start, /message och /conversation. */
  sync(fields: SessionMeterFields): void {
    if (fields.costUsd !== undefined) {
      this.costUsd.set(fields.costUsd);
      this.costComplete.set(fields.costComplete ?? true);
    }

    if (fields.elapsedMinutes === undefined || fields.sessionDurationMinutes === undefined) {
      return;
    }
    this.elapsedAtSync.set(fields.elapsedMinutes);
    this.durationMinutes.set(fields.sessionDurationMinutes);
    this.syncedAt.set(Date.now());
    this.now.set(Date.now());
  }

  markComplete(): void {
    this.complete.set(true);
  }
}
