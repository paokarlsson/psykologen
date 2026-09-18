import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

/**
 * Underlagets läge. 'peek' delar ytan med samtalet, 'full' tar över den.
 * Över brytpunkten finns bara hopfällt och utfällt - där är 'peek' kolumnen
 * vid sidan av samtalet.
 */
export type SheetState = 'closed' | 'peek' | 'full';

/**
 * Det skalet vet om sig självt och som komponenterna behöver: hur mycket av
 * sessionsunderlaget som syns, och om fliken över huvud taget är framme.
 */
@Injectable({ providedIn: 'root' })
export class UiState {
  readonly sheetState = signal<SheetState>('closed');

  readonly documentVisible = signal(true);

  /**
   * Om profil och plan faktiskt är synliga. Pollningen hänger på den: två
   * anrop var femte sekund för data ingen tittar på kostar batteri och surf.
   */
  readonly underlagVisible = computed(
    () => this.documentVisible() && this.sheetState() !== 'closed',
  );

  constructor() {
    this.documentVisible.set(document.visibilityState === 'visible');

    const onVisibilityChange = () =>
      this.documentVisible.set(document.visibilityState === 'visible');

    document.addEventListener('visibilitychange', onVisibilityChange);

    inject(DestroyRef).onDestroy(() => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
    });
  }
}
