import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../../services/api.service';

interface PromptField {
  key: string;
  label: string;
  description: string;
  value: string;
  default: string;
}

const FIELD_META: { key: string; label: string; description: string }[] = [
  {
    key: 'systemPrompt',
    label: 'Systemprompt - Eriks persona',
    description: 'Vem Erik är: bakgrund, personlighet, språkstil. Skickas med i varje AI-anrop.'
  },
  {
    key: 'openingInstruction',
    label: 'Öppningsinstruktion',
    description: 'Instruktionen som startar det allra första meddelandet i samtalet.'
  },
  {
    key: 'thoughtReflection',
    label: 'Reflektionsmall (Eriks tysta inre tankar)',
    description: 'Platshållare: {{userInput}}, {{currentThoughts}}'
  },
  {
    key: 'erikResponse',
    label: 'Svarsmall (det Erik säger till dig)',
    description: 'Platshållare: {{currentThoughts}}, {{sessionPlan}}, {{sessionTimeMinutes}}, {{sessionDurationMinutes}}, {{remainingMinutes}}, {{userInput}}'
  },
  {
    key: 'profileUpdate',
    label: 'Profiluppdatering (analys av dig som patient)',
    description: 'Måste svara i två delar, rubrikerna ordagrant: "===ÄNDRINGAR===" (kort ändringslogg - visas under Historik) följt av "===DOKUMENT===" (hela profilen). Tar du bort rubrikerna loggas ingen historik den turen. Platshållare: {{existingProfile}}, {{userInput}}, {{agentResponse}}'
  },
  {
    key: 'planUpdate',
    label: 'Planuppdatering (sessionsplanen)',
    description: 'Måste svara i två delar, rubrikerna ordagrant: "===ÄNDRINGAR===" (kort ändringslogg - visas under Historik) följt av "===DOKUMENT===" (hela planen). Tar du bort rubrikerna loggas ingen historik den turen. Platshållare: {{existingPlan}}, {{userInput}}, {{agentResponse}}, {{timingAnalysis}}, {{elapsedMinutes}}, {{sessionDurationMinutes}}, {{remainingMinutes}}'
  },
];

@Component({
  selector: 'app-settings',
  imports: [FormsModule, NgClass],
  templateUrl: './settings.html',
  styleUrl: './settings.css'
})
export class Settings {
  isOpen = false;
  isLoading = false;
  useCustomPrompts = false;
  fields: PromptField[] = [];
  statusMessage: string | null = null;
  errorMessage: string | null = null;
  savingKey: string | null = null;

  sessionDurationMinutes = 45;
  defaultSessionDurationMinutes = 45;
  isSavingDuration = false;

  constructor(private apiService: ApiService) { }

  toggleOpen(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen && this.fields.length === 0) {
      this.loadSettings();
    }
  }

  loadSettings(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.apiService.getPromptSettings().subscribe({
      next: (response) => {
        this.useCustomPrompts = response.useCustomPrompts;
        this.fields = FIELD_META.map(meta => ({
          ...meta,
          value: response.prompts[meta.key] ?? '',
          default: response.defaults[meta.key] ?? '',
        }));
        this.sessionDurationMinutes = response.sessionDurationMinutes;
        this.defaultSessionDurationMinutes = response.defaultSessionDurationMinutes;
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        console.error('Kunde inte läsa promptinställningar:', error);
        this.errorMessage = 'Kunde inte läsa promptinställningar.';
        this.isLoading = false;
      }
    });
  }

  isModified(field: PromptField): boolean {
    return field.value !== field.default;
  }

  onToggleCustomPrompts(): void {
    const enabled = this.useCustomPrompts;
    this.apiService.setCustomPromptsEnabled(enabled).subscribe({
      next: () => {
        this.statusMessage = enabled ? 'Egna promptar aktiverade.' : 'Kör nu med standardpromptarna igen.';
        this.errorMessage = null;
      },
      error: () => {
        this.errorMessage = 'Kunde inte ändra inställningen.';
        this.useCustomPrompts = !enabled;
      }
    });
  }

  save(field: PromptField): void {
    this.savingKey = field.key;
    this.statusMessage = null;
    this.errorMessage = null;
    this.apiService.updatePrompts({ [field.key]: field.value }).subscribe({
      next: () => {
        this.useCustomPrompts = true;
        this.statusMessage = `"${field.label}" sparad.`;
        this.savingKey = null;
      },
      error: () => {
        this.errorMessage = `Kunde inte spara "${field.label}".`;
        this.savingKey = null;
      }
    });
  }

  resetField(field: PromptField): void {
    this.errorMessage = null;
    this.apiService.resetPrompt(field.key).subscribe({
      next: () => {
        field.value = field.default;
        this.statusMessage = `"${field.label}" återställd till standard.`;
      },
      error: () => {
        this.errorMessage = `Kunde inte återställa "${field.label}".`;
      }
    });
  }

  isDurationModified(): boolean {
    return this.sessionDurationMinutes !== this.defaultSessionDurationMinutes;
  }

  saveSessionDuration(): void {
    if (!this.sessionDurationMinutes || this.sessionDurationMinutes <= 0) {
      this.errorMessage = 'Sessionslängden måste vara större än 0 minuter.';
      return;
    }
    this.isSavingDuration = true;
    this.errorMessage = null;
    this.apiService.setSessionDuration(this.sessionDurationMinutes).subscribe({
      next: () => {
        this.statusMessage = `Sessionslängd satt till ${this.sessionDurationMinutes} minuter.`;
        this.isSavingDuration = false;
      },
      error: () => {
        this.errorMessage = 'Kunde inte spara sessionslängden.';
        this.isSavingDuration = false;
      }
    });
  }

  resetSessionDuration(): void {
    this.sessionDurationMinutes = this.defaultSessionDurationMinutes;
    this.saveSessionDuration();
  }

  resetSession(): void {
    if (!confirm('Starta om sessionen helt blankt? Samtalshistorik, profil och plan raderas. Dina promptar påverkas inte.')) {
      return;
    }
    this.apiService.resetSession().subscribe({
      next: () => window.location.reload(),
      error: () => {
        this.errorMessage = 'Kunde inte starta om sessionen.';
      }
    });
  }
}
