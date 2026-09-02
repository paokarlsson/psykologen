import { Component, OnInit, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, Message, MessageResponse } from '../../services/api.service';

@Component({
  selector: 'app-chat',
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.css'
})
export class Chat implements OnInit, AfterViewInit {
  @ViewChild('messageInput') messageInput!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('messagesContainer') messagesContainer!: ElementRef<HTMLDivElement>;
  
  messages: Message[] = [];
  currentMessage: string = '';
  isConversationStarted: boolean = false;
  isLoading: boolean = false;
  sessionComplete: boolean = false;
  backendError: string | null = null;

  constructor(private apiService: ApiService) { }
  
  ngAfterViewInit(): void {
    // Focus input field when it becomes available
    if (this.isConversationStarted && this.messageInput) {
      setTimeout(() => this.messageInput.nativeElement.focus(), 100);
    }
  }

  ngOnInit(): void {
    this.loadConversation();
  }

  startConversation(): void {
    this.isLoading = true;
    this.backendError = null;
    this.apiService.startConversation().subscribe({
      next: (response: MessageResponse) => {
        if (response.success) {
          this.isConversationStarted = true;
          this.messages.push({
            role: 'assistant',
            content: response.message,
            timestamp: Date.now()
          });
          // Focus input field and scroll to bottom after conversation starts
          setTimeout(() => {
            if (this.messageInput) {
              this.messageInput.nativeElement.focus();
            }
            this.scrollToBottom();
          }, 100);
        }
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        console.error('Error starting conversation:', error);
        this.backendError = this.describeError(error);
        this.isLoading = false;
      }
    });
  }

  sendMessage(): void {
    if (!this.currentMessage.trim() || this.isLoading || this.sessionComplete) {
      return;
    }

    // Add user message to chat
    this.messages.push({
      role: 'user',
      content: this.currentMessage,
      timestamp: Date.now()
    });

    const messageToSend = this.currentMessage;
    this.currentMessage = '';
    this.isLoading = true;
    this.backendError = null;

    // Scroll to bottom after adding user message
    setTimeout(() => this.scrollToBottom(), 0);

    this.apiService.sendMessage(messageToSend).subscribe({
      next: (response: MessageResponse) => {
        if (response.success) {
          this.messages.push({
            role: 'assistant',
            content: response.message,
            timestamp: Date.now()
          });

          if (response.sessionComplete) {
            this.sessionComplete = true;
          }
          // Focus input field and scroll to bottom after receiving response
          setTimeout(() => {
            if (this.messageInput) {
              this.messageInput.nativeElement.focus();
            }
            this.scrollToBottom();
          }, 100);
        }
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        console.error('Error sending message:', error);
        this.backendError = this.describeError(error);
        this.isLoading = false;
      }
    });
  }

  loadConversation(): void {
    this.apiService.getConversation().subscribe({
      next: (response) => {
        this.backendError = null;
        if (response.success && response.conversation.length > 1) {
          // Filter out system message and set messages
          this.messages = response.conversation.filter(msg => msg.role !== 'system');
          this.isConversationStarted = this.messages.length > 0;
          // Scroll to bottom after loading conversation
          setTimeout(() => this.scrollToBottom(), 100);
        }
      },
      error: (error: HttpErrorResponse) => {
        console.error('Error loading conversation:', error);
        this.backendError = this.describeError(error);
      }
    });
  }

  retryConnection(): void {
    this.loadConversation();
  }

  private describeError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Kan inte nå backend. Kontrollera att den är startad (t.ex. "cd backend && mvn spring-boot:run" eller "docker compose up").';
    }
    return `Backend svarade med ett fel (${error.status}). Försök igen om en stund.`;
  }

  onKeyPress(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  formatTimestamp(timestamp?: number): string {
    if (!timestamp) return '';
    return new Date(timestamp).toLocaleTimeString();
  }

  private scrollToBottom(): void {
    if (this.messagesContainer) {
      const container = this.messagesContainer.nativeElement;
      container.scrollTop = container.scrollHeight;
    }
  }
}
