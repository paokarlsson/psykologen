import { Component, input, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-loading-spinner',
  imports: [],
  templateUrl: './loading-spinner.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './loading-spinner.css'
})
export class LoadingSpinner {
  /** Text som visas bredvid punkterna, t.ex. "Laddar plan…". */
  readonly label = input<string>('Laddar…');
  /** Kompakt läge utan text - för användning i t.ex. en liten ikonknapp. */
  readonly compact = input<boolean>(false);
  /**
   * Sätt till false för indikatorer som blinkar till ofta (t.ex. var 5:e
   * sekund vid autouppdatering) - annars avbryts skärmläsare upprepade
   * gånger av en region som konstant dyker upp och försvinner.
   */
  readonly announce = input<boolean>(true);
}
