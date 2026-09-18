import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'alert'
  | 'arrow-down'
  | 'check'
  | 'chevron-down'
  | 'chevron-up'
  | 'clipboard'
  | 'clock'
  | 'eye'
  | 'list'
  | 'more'
  | 'power'
  | 'refresh'
  | 'send'
  | 'sliders'
  | 'user'
  | 'x';

/**
 * Inline-SVG i stället för emoji. Emoji ritas av operativsystemet: de byter
 * utseende mellan plattformar, kommer i färger som inte hör till paletten och
 * går inte att ge en streckvikt. Ikonerna här ärver textfärgen via
 * currentColor och storleken via font-size, så en knapp som byter färg tar
 * med sig ikonen.
 */
@Component({
  selector: 'app-icon',
  templateUrl: './icon.html',
  styleUrl: './icon.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class Icon {
  readonly name = input.required<IconName>();
}
