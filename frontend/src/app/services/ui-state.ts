import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

/** Underlagets läge på mobil. Över brytpunkten står det alltid framme. */
export type SheetState = 'closed' | 'peek' | 'full';

const WIDE_LAYOUT = '(min-width: 900px)';

/**
 * Det skalet vet om sig självt och som komponenterna behöver: hur mycket av
 * sessionsunderlaget som syns, och om fliken över huvud taget är framme.
 */
@Injectable({ providedIn: 'root' })
export class UiState {
  readonly sheetState = signal<SheetState>('closed');

  readonly wideLayout = signal(false);
  readonly documentVisible = signal(true);

  /**
   * Om profil och plan faktiskt är synliga. Pollningen hänger på den: två
   * anrop var femte sekund för data ingen tittar på kostar batteri och surf.
   */
  readonly underlagVisible = computed(
    () => this.documentVisible() && (this.wideLayout() || this.sheetState() !== 'closed'),
  );

  constructor() {
    const media = matchMedia(WIDE_LAYOUT);
    this.wideLayout.set(media.matches);
    this.documentVisible.set(document.visibilityState === 'visible');

    const onLayoutChange = (event: MediaQueryListEvent) => this.wideLayout.set(event.matches);
    const onVisibilityChange = () =>
      this.documentVisible.set(document.visibilityState === 'visible');

    media.addEventListener('change', onLayoutChange);
    document.addEventListener('visibilitychange', onVisibilityChange);

    inject(DestroyRef).onDestroy(() => {
      media.removeEventListener('change', onLayoutChange);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    });
  }
}
