import { Component } from '@angular/core';
import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, HistoryEntryDto } from '../../services/api.service';

@Component({
  selector: 'app-history',
  imports: [NgClass],
  templateUrl: './history.html',
  styleUrl: './history.css'
})
export class History {
  isOpen = false;
  isLoading = false;
  errorMessage: string | null = null;
  entries: HistoryEntryDto[] = [];

  constructor(private apiService: ApiService) { }

  toggleOpen(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.load();
    }
  }

  load(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.apiService.getHistory().subscribe({
      next: (response) => {
        this.entries = response.history;
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        console.error('Kunde inte läsa historiken:', error);
        this.errorMessage = 'Kunde inte läsa historiken.';
        this.isLoading = false;
      }
    });
  }

  typeLabel(entry: HistoryEntryDto): string {
    return entry.type === 'profile' ? '👤 Profil' : '🗒️ Plan';
  }

  formatTime(entry: HistoryEntryDto): string {
    return new Date(entry.timestamp).toLocaleTimeString();
  }
}
