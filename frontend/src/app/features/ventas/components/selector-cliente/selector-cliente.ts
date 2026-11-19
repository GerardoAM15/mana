import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, model, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeError } from '../../../../core/utils/http-error';
import { Cliente } from '../../../clientes/models/cliente.model';
import { ClienteService } from '../../../clientes/services/cliente-service';

const DNI = /^\d{8}$/;

// Vincula opcionalmente una venta a un cliente registrado, buscándolo por DNI.
// Solo acepta clientes activos con el DNI exacto; sin cliente la venta se registra igual.
@Component({
  selector: 'app-selector-cliente',
  imports: [RouterLink],
  templateUrl: './selector-cliente.html',
  styleUrl: './selector-cliente.css',
})
export class SelectorCliente {
  private readonly clientes = inject(ClienteService);

  readonly cliente = model<Cliente | null>(null);
  readonly deshabilitado = input(false);

  protected readonly dni = signal('');
  protected readonly buscando = signal(false);
  protected readonly aviso = signal<string | null>(null);
  protected readonly noRegistrado = signal(false);

  protected escribir(valor: string): void {
    this.dni.set(valor.replace(/\D/g, '').slice(0, 8));
    this.aviso.set(null);
    this.noRegistrado.set(false);
  }

  buscar(): void {
    const dni = this.dni();
    if (!DNI.test(dni)) {
      this.aviso.set('Ingrese un DNI de 8 dígitos.');
      return;
    }
    this.buscando.set(true);
    this.aviso.set(null);
    this.noRegistrado.set(false);
    this.clientes.buscar(dni, 0, 5).subscribe({
      next: pagina => {
        this.buscando.set(false);
        const encontrado = pagina.contenido.find(c => c.dni === dni && c.activo);
        if (encontrado) {
          this.cliente.set(encontrado);
          this.dni.set('');
        } else {
          this.noRegistrado.set(true);
          this.aviso.set('No hay un cliente activo con ese DNI.');
        }
      },
      error: (err: HttpErrorResponse) => {
        this.buscando.set(false);
        this.aviso.set(mensajeError(err));
      },
    });
  }

  quitar(): void {
    this.cliente.set(null);
    this.aviso.set(null);
    this.noRegistrado.set(false);
  }
}
