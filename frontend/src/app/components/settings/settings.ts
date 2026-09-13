import { Component, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, ContextStrategyDto } from '../../services/api.service';
import { LoadingSpinner } from '../../shared/loading-spinner/loading-spinner';

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
    description: 'Svarar i två delar, rubrikerna ordagrant: "===ÄNDRINGAR===" (vad som lades till, skrevs om eller ströks - visas under Historik) följt av "===DOKUMENT===" (hela den reviderade tankelistan, som ersätter den gamla). Utan rubrikerna faller mallen tillbaka på det gamla beteendet: svaret läggs till som nya tankar och inget stryks. Platshållare: {{userInput}}, {{currentThoughts}}'
  },
  {
    key: 'interventionChoice',
    label: 'Metodval (vilket grepp Erik ska använda)',
    description: 'Väljer ett terapeutiskt grepp - öppen fråga, spegling, skalfråga och så vidare - innan Erik formulerar sin replik. Ska svara med enbart greppets id. Känns inget id igen i svaret används öppen fråga. Själva katalogen av grepp ligger i koden, {{interventionList}} fyller i den. Platshållare: {{interventionList}}, {{currentThoughts}}, {{sessionPlan}}, {{elapsedMinutes}}, {{sessionDurationMinutes}}, {{remainingMinutes}}, {{userInput}}'
  },
  {
    key: 'erikResponse',
    label: 'Svarsmall (det Erik säger till dig)',
    description: 'Platshållare: {{currentThoughts}}, {{patientProfile}}, {{sessionPlan}}, {{openThreads}}, {{intervention}} (greppet metodvalet landade i), {{sessionTimeMinutes}}, {{sessionDurationMinutes}}, {{remainingMinutes}}, {{userInput}}'
  },
  {
    key: 'profileUpdate',
    label: 'Profiluppdatering (analys av dig som patient)',
    description: 'Körs före Eriks svar, så att han svarar på en profil som redan innehåller det du just skrev. Utdraget är därför Eriks föregående replik ({{previousResponse}}) plus din nya ({{userInput}}). Måste svara i två delar, rubrikerna ordagrant: "===ÄNDRINGAR===" (kort ändringslogg - visas under Historik) följt av "===DOKUMENT===" (hela profilen). Tar du bort rubrikerna loggas ingen historik den turen. Platshållare: {{existingProfile}}, {{previousResponse}}, {{userInput}}'
  },
  {
    key: 'threadsUpdate',
    label: 'Öppna trådar (nämnt men aldrig utvecklat)',
    description: 'Håller en kort lista över trådar du öppnat och släppt, så att Erik kan återkomma till dem. Körs före svaret, med samma omparade utdrag som profilen. Måste svara i två delar, rubrikerna ordagrant: "===ÄNDRINGAR===" (vilka trådar som öppnades och stängdes - visas under Historik) följt av "===DOKUMENT===" (hela listan, en tråd per rad). Platshållare: {{existingThreads}}, {{previousResponse}}, {{userInput}}'
  },
  {
    key: 'planUpdate',
    label: 'Planuppdatering (sessionsplanen)',
    description: 'Måste svara i två delar, rubrikerna ordagrant: "===ÄNDRINGAR===" (kort ändringslogg - visas under Historik) följt av "===DOKUMENT===" (hela planen). Tar du bort rubrikerna loggas ingen historik den turen. Platshållare: {{existingPlan}}, {{userInput}}, {{agentResponse}}, {{timingAnalysis}}, {{elapsedMinutes}}, {{sessionDurationMinutes}}, {{remainingMinutes}}'
  },
];

@Component({
  selector: 'app-settings',
  imports: [FormsModule, NgClass, LoadingSpinner],
  templateUrl: './settings.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './settings.css'
})
export class Settings {
  @ViewChild('toggleBtn') private toggleBtnRef?: ElementRef<HTMLButtonElement>;
  @ViewChild('panel') private panelRef?: ElementRef<HTMLDivElement>;

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

  contextStrategy = 'full';
  contextStrategies: ContextStrategyDto[] = [];

  constructor(private apiService: ApiService) { }

  toggleOpen(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      if (this.fields.length === 0) {
        this.loadSettings();
      }
      setTimeout(() => this.panelRef?.nativeElement.focus());
    }
  }

  close(): void {
    this.isOpen = false;
    this.toggleBtnRef?.nativeElement.focus();
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
        this.contextStrategy = response.contextStrategy;
        this.contextStrategies = response.contextStrategies;
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

  strategyDescription(): string {
    return this.contextStrategies.find(s => s.id === this.contextStrategy)?.description ?? '';
  }

  onChangeContextStrategy(): void {
    const valt = this.contextStrategy;
    this.errorMessage = null;
    this.apiService.setContextStrategy(valt).subscribe({
      next: () => {
        const label = this.contextStrategies.find(s => s.id === valt)?.label ?? valt;
        this.statusMessage = `Kontextstrategi satt till "${label}". Gäller från nästa meddelande.`;
      },
      error: () => {
        this.errorMessage = 'Kunde inte byta kontextstrategi.';
      }
    });
  }

  resetSession(): void {
    this.apiService.resetSession().subscribe({
      next: () => window.location.reload(),
      error: () => {
        this.errorMessage = 'Kunde inte starta om sessionen.';
      }
    });
  }
}
