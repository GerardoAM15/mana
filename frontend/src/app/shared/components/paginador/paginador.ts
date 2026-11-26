import { Component, input, output } from '@angular/core';
import { PaginaResponse } from '../../../core/models/pagina-response';

// Uso: <app-paginador [pagina]="resultado()" (cambiar)="irA($event)" />
@Component({
  selector: 'app-paginador',
  templateUrl: './paginador.html',
  styleUrl: './paginador.css',
})
export class Paginador {
  readonly pagina = input.required<PaginaResponse<unknown>>();
  readonly deshabilitado = input(false);
  readonly cambiar = output<number>();
  readonly cambiarTamanio = output<number>();
  readonly tamanios = [5, 10, 20];

  seleccionarTamanio(selector: HTMLSelectElement): void {
    const tamanio = Number(selector.value);
    // El selector refleja el tamaño confirmado por la API.
    selector.value = String(this.pagina().tamanio);
    this.cambiarTamanio.emit(tamanio);
  }

  anterior(): void {
    const actual = this.pagina().pagina;
    if (actual > 0) this.cambiar.emit(actual - 1);
  }

  siguiente(): void {
    const pagina = this.pagina();
    if (!pagina.ultima) this.cambiar.emit(pagina.pagina + 1);
  }
}
