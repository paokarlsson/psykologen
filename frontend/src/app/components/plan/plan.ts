import { Component, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { pollingResource } from '../../shared/polling-resource';

const EMPTY_PLAN = 'Ingen plan skapad än.';

@Component({
  selector: 'app-plan',
  imports: [],
  templateUrl: './plan.html',
  styleUrl: './plan.css'
})
export class Plan {
  private readonly apiService = inject(ApiService);

  private readonly planResource = pollingResource(
    () => this.apiService.getPlan().pipe(map((response) => response.plan)),
    EMPTY_PLAN
  );

  readonly plan = this.planResource.value;
  readonly isLoading = this.planResource.isLoading;
  readonly lastUpdated = this.planResource.lastUpdated;

  refreshPlan(): void {
    this.planResource.refresh();
  }

  formatTimestamp(): string {
    const lastUpdated = this.lastUpdated();
    return lastUpdated ? lastUpdated.toLocaleTimeString() : '';
  }

  isPlanEmpty(): boolean {
    return this.plan().trim().length === 0 || this.plan() === EMPTY_PLAN;
  }
}
