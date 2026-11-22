import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { guardarServidor, normalizarServidor, servidorGuardado } from '../../core/config/servidor';

// Recarga la app para que todos los servicios usen la nueva dirección.
export const RECARGAR = { ir: (url: string) => globalThis.location.assign(url) };

@Component({
  selector: 'app-servidor',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './servidor.html',
  styleUrl: './servidor.css',
})
export class Servidor {
  private readonly http = inject(HttpClient);

  protected readonly actual = servidorGuardado();
  protected readonly probando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    url: [this.actual ?? '', [Validators.required, Validators.maxLength(200)]],
  });

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const url = normalizarServidor(this.form.getRawValue().url);
    this.probando.set(true);
    this.error.set(null);
    // /api/health responde texto plano, no JSON.
    this.http.get(`${url}/api/health`, { responseType: 'text' }).subscribe({
      next: () => {
        guardarServidor(url);
        RECARGAR.ir('/login');
      },
      error: () => {
        this.error.set(`No se pudo conectar con ${url}. Revise que el servidor esté encendido y en la misma red.`);
        this.probando.set(false);
      },
    });
  }
}
