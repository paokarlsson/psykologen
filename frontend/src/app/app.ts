import { Component, inject, signal, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { Chat } from './components/chat/chat';
import { Profile } from './components/profile/profile';
import { Plan } from './components/plan/plan';
import { Settings } from './components/settings/settings';
import { History } from './components/history/history';
import { Login } from './components/login/login';
import { AuthService } from './services/auth.service';

export type View = 'chat' | 'profile' | 'plan';

@Component({
  selector: 'app-root',
  imports: [Chat, Profile, Plan, Settings, History, Login],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.css'
})
export class App {
  readonly auth = inject(AuthService);

  /** Styr vilken panel som visas på mobil. Över brytpunkten visas alla samtidigt. */
  readonly view = signal<View>('chat');

  @ViewChild(Chat) private chat?: Chat;

  constructor() {
    this.auth.checkSession();
  }

  setView(view: View): void {
    this.view.set(view);
    // Ett element som varit display: none nollställer scrollTop när det visas
    // igen - utan detta landar man överst i samtalet i stället för längst ned.
    if (view === 'chat') {
      setTimeout(() => this.chat?.scrollToBottom());
    }
  }

  logout(): void {
    this.auth.logout().subscribe({
      // Misslyckas anropet är sessionen ändå slut lokalt sett.
      error: () => this.auth.markLoggedOut(),
    });
  }
}
