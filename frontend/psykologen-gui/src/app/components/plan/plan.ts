import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-plan',
  imports: [CommonModule],
  templateUrl: './plan.html',
  styleUrl: './plan.css'
})
export class Plan implements OnInit, OnDestroy {
  plan: string = 'Ingen plan skapad än.';
  isLoading: boolean = false;
  lastUpdated?: Date;
  private updateSubscription?: Subscription;

  constructor(private apiService: ApiService) { }

  ngOnInit(): void {
    this.loadPlan();
    // Auto-refresh every 5 seconds
    this.updateSubscription = interval(5000).subscribe(() => {
      this.loadPlan();
    });
  }

  ngOnDestroy(): void {
    if (this.updateSubscription) {
      this.updateSubscription.unsubscribe();
    }
  }

  loadPlan(): void {
    this.isLoading = true;
    this.apiService.getPlan().subscribe({
      next: (response) => {
        if (response.success) {
          this.plan = response.plan;
          this.lastUpdated = new Date();
        }
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading plan:', error);
        this.isLoading = false;
      }
    });
  }

  refreshPlan(): void {
    this.loadPlan();
  }

  formatTimestamp(): string {
    if (!this.lastUpdated) return '';
    return this.lastUpdated.toLocaleTimeString();
  }

  isPlanEmpty(): boolean {
    return this.plan === 'Ingen plan skapad än.' || this.plan.trim().length === 0;
  }
}
