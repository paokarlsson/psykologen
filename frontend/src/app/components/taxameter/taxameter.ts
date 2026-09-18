import { ChangeDetectionStrategy, Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FEEDBACK_KRONOR, GRATIS_KRONOR, SessionMeter } from '../../services/session-meter';
import { Icon } from '../../shared/icon/icon';

/**
 * Vad sessionen kostat hittills, och vad det innebär. Beloppet ensamt säger
 * inget om var gränserna går, så prisstegen ligger bakom siffran i stället
 * för i baren.
 */
@Component({
  selector: 'app-taxameter',
  imports: [Icon],
  templateUrl: './taxameter.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './taxameter.css'
})
export class Taxameter {
  readonly meter = inject(SessionMeter);

  readonly isOpen = signal(false);

  readonly gratisKronor = GRATIS_KRONOR;
  readonly feedbackKronor = FEEDBACK_KRONOR;

  @ViewChild('toggleBtn') private toggleBtnRef?: ElementRef<HTMLButtonElement>;
  @ViewChild('panel') private panelRef?: ElementRef<HTMLElement>;

  toggleOpen(): void {
    this.isOpen.update((open) => !open);
    if (this.isOpen()) {
      setTimeout(() => this.panelRef?.nativeElement.focus());
    }
  }

  close(): void {
    this.isOpen.set(false);
    this.toggleBtnRef?.nativeElement.focus();
  }
}
