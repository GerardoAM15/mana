import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../../core/auth/auth.service';
import { claveSegura, DatosPersonales } from '../../../../core/utils/clave';
import { mensajeError } from '../../../../core/utils/http-error';
import { MedidorClave } from '../../../../shared/components/medidor-clave/medidor-clave';

function clavesIguales(grupo: AbstractControl): ValidationErrors | null {
  const { nuevaClave, confirmacion } = grupo.value as { nuevaClave: string; confirmacion: string };
  return nuevaClave === confirmacion ? null : { distintas: true };
}

// Cambio de contraseña con la sesión iniciada. Es obligatorio cuando la clave actual no cumple la política.
@Component({
  selector: 'app-cambiar-clave',
  imports: [ReactiveFormsModule, MedidorClave],
  templateUrl: './cambiar-clave.html',
  styleUrl: './cambiar-clave.css',
})
export class CambiarClave {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly obligatorio = this.auth.debeCambiarClave();

  // El nombre de la sesión sirve para avisar si la clave lo contiene; el backend revisa además correo y DNI.
  protected readonly datos = computed<DatosPersonales>(() => ({ nombre: this.auth.usuario()?.nombreCompleto ?? '' }));

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      claveActual: ['', [Validators.required, Validators.maxLength(72)]],
      nuevaClave: ['', [Validators.required, claveSegura(() => this.datos())]],
      confirmacion: ['', Validators.required],
    },
    { validators: clavesIguales },
  );
  protected readonly nuevaClave = toSignal(this.form.controls.nuevaClave.valueChanges, { initialValue: '' });

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { claveActual, nuevaClave } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.auth.cambiarClave({ claveActual, nuevaClave }).subscribe({
      next: () => void this.router.navigateByUrl('/inicio'),
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeError(err));
        this.enviando.set(false);
      },
    });
  }

  salir(): void {
    this.auth.cerrarSesion();
  }
}
