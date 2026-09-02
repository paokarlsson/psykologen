import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-profile',
  imports: [CommonModule],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class Profile implements OnInit, OnDestroy {
  profile: string = 'Ingen profil skapad än.';
  isLoading: boolean = false;
  lastUpdated?: Date;
  private updateSubscription?: Subscription;

  constructor(private apiService: ApiService) { }

  ngOnInit(): void {
    this.loadProfile();
    // Auto-refresh every 5 seconds
    this.updateSubscription = interval(5000).subscribe(() => {
      this.loadProfile();
    });
  }

  ngOnDestroy(): void {
    if (this.updateSubscription) {
      this.updateSubscription.unsubscribe();
    }
  }

  loadProfile(): void {
    this.isLoading = true;
    this.apiService.getProfile().subscribe({
      next: (response) => {
        if (response.success) {
          this.profile = response.profile;
          this.lastUpdated = new Date();
        }
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading profile:', error);
        this.isLoading = false;
      }
    });
  }

  refreshProfile(): void {
    this.loadProfile();
  }

  formatTimestamp(): string {
    if (!this.lastUpdated) return '';
    return this.lastUpdated.toLocaleTimeString();
  }

  isProfileEmpty(): boolean {
    return this.profile === 'Ingen profil skapad än.' || this.profile.trim().length === 0;
  }
}
