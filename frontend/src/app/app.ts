import { Component, inject } from '@angular/core';
import { Chat } from './components/chat/chat';
import { Profile } from './components/profile/profile';
import { Plan } from './components/plan/plan';
import { Settings } from './components/settings/settings';
import { History } from './components/history/history';
import { Login } from './components/login/login';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [Chat, Profile, Plan, Settings, History, Login],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly auth = inject(AuthService);

  constructor() {
    this.auth.checkSession();
  }

  logout(): void {
    this.auth.logout().subscribe({
      // Misslyckas anropet är sessionen ändå slut lokalt sett.
      error: () => this.auth.markLoggedOut(),
    });
  }
}
