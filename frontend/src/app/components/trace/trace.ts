import { Component, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, LlmCallDto, Message, TraceStep, TraceSummaryDto } from '../../services/api.service';
import { LoadingSpinner } from '../../shared/loading-spinner/loading-spinner';
import { Icon } from '../../shared/icon/icon';

const STEG_ETIKETT: Record<TraceStep, string> = {
  oppning: 'Öppning',
  reflektion: 'Reflektion',
  metod: 'Metodval',
  svar: 'Svar',
  profil: 'Profil',
  tradar: 'Trådar',
  plan: 'Plan',
};

const TOM_SAMMANFATTNING: TraceSummaryDto = {
  callCount: 0,
  totalInputTokens: 0,
  totalOutputTokens: 0,
  totalCostUsd: 0,
  costComplete: true,
  avgLatencyMs: 0,
  currentContextTokens: 0,
  errorCount: 0,
};

@Component({
  selector: 'app-trace',
  imports: [NgClass, LoadingSpinner, Icon],
  templateUrl: './trace.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './trace.css',
  // En öppen panel måste måla över syskonens togglar i verktygsmenyn.
  host: { '[class.panel-open]': 'isOpen' }
})
export class Trace {
  @ViewChild('toggleBtn') private toggleBtnRef?: ElementRef<HTMLButtonElement>;
  @ViewChild('panel') private panelRef?: ElementRef<HTMLDivElement>;

  isOpen = false;
  isLoading = false;
  errorMessage: string | null = null;
  calls: LlmCallDto[] = [];
  thoughts: string[] = [];
  openThreads: string[] = [];
  summary: TraceSummaryDto = TOM_SAMMANFATTNING;

  /** startedAt för de anrop användaren fällt ut. Unikt nog inom en session. */
  private expanded = new Set<number>();

  constructor(private apiService: ApiService) { }

  toggleOpen(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.load();
      setTimeout(() => this.panelRef?.nativeElement.focus());
    }
  }

  close(): void {
    this.isOpen = false;
    this.toggleBtnRef?.nativeElement.focus();
  }

  load(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.apiService.getTrace().subscribe({
      next: (response) => {
        this.calls = response.calls;
        this.thoughts = response.thoughts;
        this.openThreads = response.openThreads;
        this.summary = response.summary;
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        console.error('Kunde inte läsa traceen:', error);
        this.errorMessage = 'Kunde inte läsa traceen.';
        this.isLoading = false;
      }
    });
  }

  toggleCall(call: LlmCallDto): void {
    if (this.expanded.has(call.startedAt)) {
      this.expanded.delete(call.startedAt);
    } else {
      this.expanded.add(call.startedAt);
    }
  }

  isExpanded(call: LlmCallDto): boolean {
    return this.expanded.has(call.startedAt);
  }

  stepLabel(call: LlmCallDto): string {
    return STEG_ETIKETT[call.steg] ?? call.steg;
  }

  /** Kostnad med tillräckligt många decimaler för att ett enskilt anrop inte ska bli "0". */
  formatCost(costUsd: number | null): string {
    return costUsd === null ? 'okänt pris' : '$' + costUsd.toFixed(5);
  }

  formatTotalCost(): string {
    const belopp = '$' + this.summary.totalCostUsd.toFixed(4);
    return this.summary.costComplete ? belopp : 'minst ' + belopp;
  }

  roleLabel(message: Message): string {
    if (message.role === 'system') return 'system';
    if (message.role === 'assistant') return 'assistant (Erik)';
    return 'user';
  }
}
