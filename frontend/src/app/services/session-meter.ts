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
 * Fast växelkurs. Prislistan hos AI-leverantören är i dollar, taxametern visas
 * i kronor - ingen hämtar en dagskurs för det här, och behöver inte: beloppen
 * rör sig om ental kronor och siffran är ändå en uppskattning.
 */
export const KRONOR_PER_DOLLAR = 10.5;

/** Under den här gränsen bjuder systemutvecklaren. */
export const GRATIS_KRONOR = 20;

/** Under den här gränsen är det fortfarande gratis, mot feedback. */
export const FEEDBACK_KRONOR = 50;

export type Prissteg = 'bjuden' | 'feedback' | 'swish';

export function prisstegFor(kronor: number): Prissteg {
  if (kronor < GRATIS_KRONOR) {
    return 'bjuden';
  }
  return kronor < FEEDBACK_KRONOR ? 'feedback' : 'swish';
}

/**
 * Delas av glaslådan och skalet, så att beloppen aldrig kan skilja sig åt.
 * "minst" när något anrop kördes på en modell utan pris i prislistan - summan
 * är då för låg, inte okänd. Under en krona är avrundningen meningslös, och
 * "0 kr" skulle se ut som att mätaren står stilla.
 */
export function formatTotalCost(costUsd: number, complete: boolean): string {
  const kronor = costUsd * KRONOR_PER_DOLLAR;
  const belopp = kronor < 1 ? 'under 1 kr' : `≈ ${Math.round(kronor)} kr`;
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

  readonly costKronor = computed(() => {
    const cost = this.costUsd();
    return cost === null ? null : cost * KRONOR_PER_DOLLAR;
  });

  /** Vilket av de tre prisstegen sessionen ligger i just nu. */
  readonly prissteg = computed<Prissteg>(() => prisstegFor(this.costKronor() ?? 0));

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
