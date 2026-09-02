import { Component } from '@angular/core';
import { Chat } from './components/chat/chat';
import { Profile } from './components/profile/profile';
import { Plan } from './components/plan/plan';
import { Settings } from './components/settings/settings';
import { History } from './components/history/history';

@Component({
  selector: 'app-root',
  imports: [Chat, Profile, Plan, Settings, History],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  
}
