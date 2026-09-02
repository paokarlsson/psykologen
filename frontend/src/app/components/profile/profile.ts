import { Component, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { pollingResource } from '../../shared/polling-resource';

const EMPTY_PROFILE = 'Ingen profil skapad än.';

@Component({
  selector: 'app-profile',
  imports: [],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class Profile {
  private readonly apiService = inject(ApiService);

  private readonly profileResource = pollingResource(
    () => this.apiService.getProfile().pipe(map((response) => response.profile)),
    EMPTY_PROFILE
  );

  readonly profile = this.profileResource.value;
  readonly isLoading = this.profileResource.isLoading;
  readonly lastUpdated = this.profileResource.lastUpdated;

  refreshProfile(): void {
    this.profileResource.refresh();
  }

  formatTimestamp(): string {
    const lastUpdated = this.lastUpdated();
    return lastUpdated ? lastUpdated.toLocaleTimeString() : '';
  }

  isProfileEmpty(): boolean {
    return this.profile().trim().length === 0 || this.profile() === EMPTY_PROFILE;
  }
}
