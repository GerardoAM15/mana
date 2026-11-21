import { Component, computed, input } from '@angular/core';
import { DatosPersonales, nivelClave, problemasClave } from '../../../core/utils/clave';

// Barra de fuerza y lista de lo que le falta a la contraseña mientras se escribe.
@Component({
  selector: 'app-medidor-clave',
  templateUrl: './medidor-clave.html',
  styleUrl: './medidor-clave.css',
})
export class MedidorClave {
  readonly clave = input<string>('');
  readonly datos = input<DatosPersonales>({});

  protected readonly nivel = computed(() => nivelClave(this.clave(), this.datos()));
  protected readonly faltantes = computed(() => (this.clave() ? problemasClave(this.clave(), this.datos()) : []));
  protected readonly etiqueta = computed(() => ({ vacia: '', debil: 'Débil', media: 'Aceptable', fuerte: 'Fuerte' })[this.nivel()]);
}
