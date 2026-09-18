import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Message {
  role: string;
  content: string;
  timestamp?: number;
  session_time?: number;
}

export interface ConversationResponse {
  success: boolean;
  conversation: Message[];
  /** Saknas om anropet gick till en äldre backend. */
  elapsedMinutes?: number;
  sessionDurationMinutes?: number;
}

export interface MessageResponse {
  success: boolean;
  message: string;
  role: string;
  sessionComplete?: boolean;
  /** Saknas om anropet gick till en äldre backend. */
  elapsedMinutes?: number;
  sessionDurationMinutes?: number;
}

export interface ProfileResponse {
  success: boolean;
  profile: string;
}

export interface PlanResponse {
  success: boolean;
  plan: string;
}

export interface ContextStrategyDto {
  id: string;
  label: string;
  description: string;
}

export interface PromptSettingsResponse {
  success: boolean;
  useCustomPrompts: boolean;
  prompts: Record<string, string>;
  defaults: Record<string, string>;
  sessionDurationMinutes: number;
  defaultSessionDurationMinutes: number;
  contextStrategy: string;
  contextStrategies: ContextStrategyDto[];
}

export interface SimpleResponse {
  success: boolean;
  error?: string;
}

export interface HistoryEntryDto {
  type: 'profile' | 'plan' | 'thoughts' | 'threads';
  timestamp: number;
  elapsedMinutes: number;
  change: string;
}

export interface HistoryResponse {
  success: boolean;
  history: HistoryEntryDto[];
}

export type TraceStep = 'oppning' | 'reflektion' | 'metod' | 'svar' | 'profil' | 'tradar' | 'plan';

export interface LlmCallDto {
  steg: TraceStep;
  turn: number;
  startedAt: number;
  latencyMs: number;
  /** null när anropet misslyckades innan någon modell svarade. */
  model: string | null;
  sentMessages: Message[];
  responseText: string | null;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheCreationTokens: number;
  /** null när modellen saknar pris i backendens prislista. */
  costUsd: number | null;
  /** null när anropet lyckades. */
  error: string | null;
}

export interface TraceSummaryDto {
  callCount: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalCostUsd: number;
  /** false när något anrop kördes på en modell utan känt pris - summan är då för låg. */
  costComplete: boolean;
  avgLatencyMs: number;
  currentContextTokens: number;
  errorCount: number;
}

export interface TraceResponse {
  success: boolean;
  calls: LlmCallDto[];
  thoughts: string[];
  openThreads: string[];
  summary: TraceSummaryDto;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  startConversation(): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.baseUrl}/start`, {});
  }

  sendMessage(message: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.baseUrl}/message`, { message });
  }

  getConversation(): Observable<ConversationResponse> {
    return this.http.get<ConversationResponse>(`${this.baseUrl}/conversation`);
  }

  getProfile(): Observable<ProfileResponse> {
    return this.http.get<ProfileResponse>(`${this.baseUrl}/profile`);
  }

  getPlan(): Observable<PlanResponse> {
    return this.http.get<PlanResponse>(`${this.baseUrl}/plan`);
  }

  resetSession(): Observable<SimpleResponse> {
    return this.http.post<SimpleResponse>(`${this.baseUrl}/reset`, {});
  }

  getPromptSettings(): Observable<PromptSettingsResponse> {
    return this.http.get<PromptSettingsResponse>(`${this.baseUrl}/settings/prompts`);
  }

  updatePrompts(updates: Record<string, string>): Observable<SimpleResponse> {
    return this.http.put<SimpleResponse>(`${this.baseUrl}/settings/prompts`, updates);
  }

  resetPrompt(key?: string): Observable<SimpleResponse> {
    return this.http.post<SimpleResponse>(`${this.baseUrl}/settings/prompts/reset`, key ? { key } : {});
  }

  setCustomPromptsEnabled(enabled: boolean): Observable<SimpleResponse> {
    return this.http.put<SimpleResponse>(`${this.baseUrl}/settings/custom-prompts-enabled`, { enabled });
  }

  setSessionDuration(minutes: number): Observable<SimpleResponse> {
    return this.http.put<SimpleResponse>(`${this.baseUrl}/settings/session-duration`, { minutes });
  }

  setContextStrategy(strategy: string): Observable<SimpleResponse> {
    return this.http.put<SimpleResponse>(`${this.baseUrl}/settings/context-strategy`, { strategy });
  }

  getHistory(): Observable<HistoryResponse> {
    return this.http.get<HistoryResponse>(`${this.baseUrl}/history`);
  }

  getTrace(): Observable<TraceResponse> {
    return this.http.get<TraceResponse>(`${this.baseUrl}/trace`);
  }

}