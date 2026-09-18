import { Component, computed, inject, signal, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { map } from 'rxjs';
import { Chat } from './components/chat/chat';
import { Profile } from './components/profile/profile';
import { Plan } from './components/plan/plan';
import { Settings } from './components/settings/settings';
import { History, TYP_ETIKETT } from './components/history/history';
import { Trace } from './components/trace/trace';
import { Login } from './components/login/login';
import { Icon } from './shared/icon/icon';
import { ApiService, HistoryEntryDto } from './services/api.service';
import { AuthService } from './services/auth.service';
import { SessionClock } from './services/session-clock';
import { SheetState, UiState } from './services/ui-state';
import { pollingResource } from './shared/polling-resource';

/** Kortare drag än så är en tryckning, och knappen tar hand om den i stället. */
const DRAG_THRESHOLD_PX = 40;

@Component({
  selector: 'app-root',
  imports: [Chat, Profile, Plan, Settings, History, Trace, Login, Icon],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.css'
})
export class App {
  readonly auth = inject(AuthService);
  readonly clock = inject(SessionClock);
  readonly ui = inject(UiState);

  private readonly apiService = inject(ApiService);

  /** Toppbarens verktygsmeny. Railen visar verktygen direkt och rör den inte. */
  readonly menuOpen = signal(false);

  /**
   * Draghandtaget ska säga något även när underlaget är hopfällt. Ändringslogen
   * bär redan exakt den meningen, och går långsammare än profil och plan.
   */
  private readonly historyResource = pollingResource<HistoryEntryDto[]>(
    () => this.apiService.getHistory().pipe(map((response) => response.history)),
    [],
    {
      intervalMs: 15000,
      // Skalet finns även på inloggningssidan - utan spärren blir det 401 var
      // femtonde sekund innan någon loggat in.
      enabled: computed(() => !!this.auth.user() && this.ui.documentVisible()),
    },
  );

  readonly sheetSummary = computed(() => {
    const entries = this.historyResource.value();
    const latest = entries[entries.length - 1];
    if (!latest) {
      return 'Profil och plan';
    }
    return `${TYP_ETIKETT[latest.type] ?? latest.type} · ${latest.change}`;
  });

  @ViewChild(Chat) private chat?: Chat;

  private dragStartY: number | null = null;
  private dragHandled = false;

  constructor() {
    this.auth.checkSession();
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  /** Handtaget fäller ut och in. Helskärm når man med knappen bredvid. */
  toggleSheet(): void {
    // Ett drag har redan flyttat arket - klicket som följer på pointerup ska
    // inte flytta det en gång till.
    if (this.dragHandled) {
      this.dragHandled = false;
      return;
    }
    this.setSheet(this.ui.sheetState() === 'closed' ? 'peek' : 'closed');
  }

  toggleFullSheet(): void {
    this.setSheet(this.ui.sheetState() === 'full' ? 'peek' : 'full');
  }

  collapseSheet(): void {
    this.setSheet('closed');
  }

  onHandlePointerDown(event: PointerEvent): void {
    this.dragStartY = event.clientY;
    this.dragHandled = false;
  }

  onHandlePointerUp(event: PointerEvent): void {
    const startY = this.dragStartY;
    this.dragStartY = null;
    if (startY === null) {
      return;
    }
    const upwards = startY - event.clientY;
    if (Math.abs(upwards) < DRAG_THRESHOLD_PX) {
      return;
    }
    this.dragHandled = true;
    this.setSheet(upwards > 0 ? this.higher() : this.lower());
  }

  onHandlePointerCancel(): void {
    this.dragStartY = null;
  }

  logout(): void {
    this.auth.logout().subscribe({
      // Misslyckas anropet är sessionen ändå slut lokalt sett.
      error: () => this.auth.markLoggedOut(),
    });
  }

  private higher(): SheetState {
    return this.ui.sheetState() === 'closed' ? 'peek' : 'full';
  }

  private lower(): SheetState {
    return this.ui.sheetState() === 'full' ? 'peek' : 'closed';
  }

  private setSheet(state: SheetState): void {
    if (state === this.ui.sheetState()) {
      return;
    }
    this.ui.sheetState.set(state);
    // Arket tar höjd från samtalet. Utan detta står man kvar på samma
    // scrollTop och tappar de sista replikerna ur sikte - men läser man
    // längre upp i samtalet ska man få ligga kvar där.
    setTimeout(() => this.chat?.keepPinned());
  }
}
