import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Valida el texto que realmente se enviará después de recortar espacios. */
export function textoObligatorio(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length > 0 ? null : { required: true };
}

export function entero(control: AbstractControl): ValidationErrors | null {
  return control.value == null || control.value === '' || Number.isInteger(control.value) ? null : { entero: true };
}
