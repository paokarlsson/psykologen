import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface Message {
  role: string;
  content: string;
  timestamp?: number;
  session_time?: number;
}

export interface ConversationResponse {
  success: boolean;
  conversation: Message[];
}

export interface MessageResponse {
  success: boolean;
  message: string;
  role: string;
  sessionComplete?: boolean;
}

export interface ProfileResponse {
  success: boolean;
  profile: string;
}

export interface PlanResponse {
  success: boolean;
  plan: string;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = 'http://localhost:8080/api/psykologen';

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

  getStatistics(): Observable<any> {
    return this.http.get(`${this.baseUrl}/statistics`);
  }

  checkHealth(): Observable<any> {
    return this.http.get(`${this.baseUrl}/health`);
  }
}