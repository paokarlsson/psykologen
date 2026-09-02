import { Component } from '@angular/core';
import { Chat } from './components/chat/chat';
import { Profile } from './components/profile/profile';
import { Plan } from './components/plan/plan';
import { Settings } from './components/settings/settings';

@Component({
  selector: 'app-root',
  imports: [Chat, Profile, Plan, Settings],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  
}
