import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { apiUrl, CLAVE_SERVIDOR, esEscritorio, guardarServidor, normalizarServidor, servidorGuardado } from '../../core/config/servidor';
import { servidorGuard } from '../../core/guards/servidor.guard';
import { RECARGAR, Servidor } from './servidor';

const tauri = globalThis as { __TAURI_INTERNALS__?: object };

function comoEscritorio(): void {
  tauri.__TAURI_INTERNALS__ = {};
}

afterEach(() => {
  delete tauri.__TAURI_INTERNALS__;
  localStorage.removeItem(CLAVE_SERVIDOR);
  vi.restoreAllMocks();
});

describe('configuración del servidor', () => {
  it('normaliza la dirección escrita por el usuario', () => {
    expect(normalizarServidor(' 192.168.1.10:8080/ ')).toBe('http://192.168.1.10:8080');
    expect(normalizarServidor('https://mana.local//')).toBe('https://mana.local');
  });

  it('en la web usa la API del mismo origen aunque haya algo guardado', () => {
    guardarServidor('192.168.1.10:8080');
    expect(esEscritorio()).toBe(false);
    expect(apiUrl()).toBe('/api/v1');
  });

  it('en escritorio usa el servidor guardado', () => {
    comoEscritorio();
    expect(apiUrl()).toBe('/api/v1');
    guardarServidor('192.168.1.10:8080');
    expect(servidorGuardado()).toBe('http://192.168.1.10:8080');
    expect(apiUrl()).toBe('http://192.168.1.10:8080/api/v1');
  });

  it('si el almacenamiento falla no hay servidor guardado', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    expect(servidorGuardado()).toBeNull();
  });
});

describe('servidorGuard', () => {
  const correr = () => TestBed.runInInjectionContext(() => servidorGuard({} as never, {} as never));

  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  it('en la web deja pasar', () => {
    expect(correr()).toBe(true);
  });

  it('en escritorio sin servidor manda a configurarlo', () => {
    comoEscritorio();
    const destino = correr() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(destino)).toBe('/servidor');
  });

  it('en escritorio con servidor deja pasar', () => {
    comoEscritorio();
    guardarServidor('192.168.1.10:8080');
    expect(correr()).toBe(true);
  });
});

describe('Servidor', () => {
  async function crear() {
    await TestBed.configureTestingModule({
      imports: [Servidor],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(Servidor);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) };
  }

  function escribir(el: HTMLElement, texto: string): void {
    const input = el.querySelector<HTMLInputElement>('#url')!;
    input.value = texto;
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  it('prueba la conexión, guarda y recarga en el login', async () => {
    const ir = vi.spyOn(RECARGAR, 'ir').mockImplementation(() => undefined);
    const { el, http } = await crear();
    escribir(el, '192.168.1.10:8080');
    const req = http.expectOne('http://192.168.1.10:8080/api/health');
    expect(req.request.responseType).toBe('text');
    req.flush('Backend funcionando correctamente');
    expect(localStorage.getItem(CLAVE_SERVIDOR)).toBe('http://192.168.1.10:8080');
    expect(ir).toHaveBeenCalledWith('/login');
  });

  it('si el servidor no responde muestra el error y no guarda', async () => {
    const { fixture, el, http } = await crear();
    escribir(el, '10.0.0.5:8080');
    http.expectOne('http://10.0.0.5:8080/api/health').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(el.textContent).toContain('No se pudo conectar con http://10.0.0.5:8080');
    expect(localStorage.getItem(CLAVE_SERVIDOR)).toBeNull();
  });

  it('no envía si la dirección está vacía', async () => {
    const { fixture, el, http } = await crear();
    escribir(el, '');
    fixture.detectChanges();
    http.expectNone(() => true);
    expect(el.textContent).toContain('Ingrese la dirección del servidor.');
  });

  it('con un servidor ya guardado ofrece volver sin cambiar', async () => {
    guardarServidor('192.168.1.10:8080');
    const { el } = await crear();
    expect(el.textContent).toContain('Volver sin cambiar (http://192.168.1.10:8080)');
  });
});
