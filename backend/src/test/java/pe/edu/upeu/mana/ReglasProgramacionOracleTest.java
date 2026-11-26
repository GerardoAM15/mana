package pe.edu.upeu.mana;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.mana.entity.*;
import pe.edu.upeu.mana.enums.*;
import pe.edu.upeu.mana.repository.*;
import pe.edu.upeu.mana.service.CalculadoraRaciones;
import pe.edu.upeu.mana.service.service.*;
import pe.edu.upeu.mana.dto.menu.*;
import pe.edu.upeu.mana.dto.calendario.*;
import pe.edu.upeu.mana.dto.contrato.*;
import pe.edu.upeu.mana.dto.consumo.*;
import pe.edu.upeu.mana.dto.comun.AnularRequestDTO;
import pe.edu.upeu.mana.exception.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.admin.password=","app.facturacion.habilitada=false","spring.jpa.hibernate.ddl-auto=validate"})
@EnabledIfEnvironmentVariable(named="MANA_ORACLE_TEST",matches="true")
@Import(ReglasProgramacionOracleTest.Tiempo.class)
@Transactional
class ReglasProgramacionOracleTest {
    static final LocalDate LUNES=LocalDate.of(2030,1,7);
    static class Reloj extends Clock {
        LocalDate fecha=LUNES;
        public ZoneId getZone(){return ZoneId.of("America/Lima");}
        public Clock withZone(ZoneId zona){return Clock.fixed(instant(),zona);}
        public Instant instant(){return fecha.atTime(12,0).atZone(getZone()).toInstant();}
    }
    @TestConfiguration static class Tiempo {@Bean @Primary Reloj relojReglas(){return new Reloj();}}
    // El reloj simulado nunca debe ejecutar el vencimiento global al arrancar el contexto.
    @MockitoBean pe.edu.upeu.mana.scheduler.ContratoScheduler tareasContratos;
    @MockitoBean ContextoLocalService contexto;
    @Autowired Reloj clock;
    @Autowired LocalRepository locales;
    @Autowired ServicioRepository servicios;
    @Autowired ServicioDiaAtencionRepository dias;
    @Autowired CategoriaPlatoRepository categorias;
    @Autowired PlatoRepository platos;
    @Autowired ReglaCobroRepository reglas;
    @Autowired ComponenteReglaRepository componentes;
    @Autowired TipoPlanRepository planes;
    @Autowired PlanServicioRepository planServicios;
    @Autowired TarifaServicioRepository tarifas;
    @Autowired ClienteRepository clientes;
    @Autowired ContratoRepository contratos;
    @Autowired DetalleContratoRepository detalles;
    @Autowired ProgramacionMenuRepository programaciones;
    @Autowired CompensacionRacionRepository compensaciones;
    @Autowired DiaNoAtencionRepository cierres;
    @Autowired MenuService menuService;
    @Autowired PlatoService platoService;
    @Autowired CalendarioService calendario;
    @Autowired ContratoService contratoService;
    @Autowired ConsumoService consumoService;
    @Autowired CalculadoraRaciones calculadora;
    String prefijo,servicio,entrada,segundo;
    Long localId,planId,clienteId;
    Plato platoEntrada,platoSegundo;

    @BeforeEach void preparar(){
        clock.fecha=LUNES;prefijo="REGLAS_"+UUID.randomUUID().toString().substring(0,8);
        var l=new Local();l.setNombre(prefijo);localId=locales.saveAndFlush(l).getId();
        when(contexto.localId(null)).thenReturn(localId);when(contexto.localId(localId)).thenReturn(localId);
        var usuario=new Usuario();usuario.setCorreo("reglas@example.invalid");when(contexto.usuario()).thenReturn(usuario);
        servicio=prefijo;var s=new Servicio();s.setCodigo(servicio);s.setNombre(prefijo);servicios.saveAndFlush(s);
        for(int n=1;n<=5;n++){var d=new ServicioDiaAtencion();d.setServicio(servicio);d.setDiaSemana(n);dias.save(d);}dias.flush();
        entrada=prefijo+"_E";segundo=prefijo+"_S";
        for(String codigo:List.of(entrada,segundo)){var c=new CategoriaPlato();c.setCodigo(codigo);c.setNombre(codigo);categorias.save(c);}categorias.flush();
        platoEntrada=plato("Entrada",entrada);platoSegundo=plato("Segundo",segundo);
        var regla=new ReglaCobro();regla.setNombre(prefijo);regla.setServicio(servicio);regla.setPrecioCombo(new BigDecimal("12.00"));regla.setRecargoLlevar(BigDecimal.ZERO);reglas.saveAndFlush(regla);
        for(String categoria:List.of(entrada,segundo)){var c=new ComponenteRegla();c.setReglaId(regla.getId());c.setCategoria(categoria);c.setCantidad(1L);componentes.save(c);}componentes.flush();
        var plan=new TipoPlan();plan.setNombre(prefijo);plan.setPeriodo(PeriodoPlan.SEMANAL);planId=planes.saveAndFlush(plan).getId();
        var ps=new PlanServicio();ps.setPlanId(planId);ps.setServicio(servicio);planServicios.saveAndFlush(ps);
        var t=new TarifaServicio();t.setServicio(servicio);t.setPrecio(new BigDecimal("10.00"));tarifas.saveAndFlush(t);
        var cliente=new Cliente();cliente.setDni(Integer.toString(ThreadLocalRandom.current().nextInt(10000000,99999999)));cliente.setNombre(prefijo);cliente.setApellido("Prueba");clienteId=clientes.saveAndFlush(cliente).getId();
    }
    Plato plato(String nombre,String cat){var p=new Plato();p.setNombre(prefijo+nombre);p.setCategoria(cat);return platos.saveAndFlush(p);}
    MenuResponseDTO programar(LocalDate fecha,Plato... lista){
        long version=programaciones.findByLocalIdAndFechaAndServicio(localId,fecha,servicio).map(ProgramacionMenu::getVersion).orElse(-1L);
        return menuService.guardar(new MenuRequestDTO(fecha,servicio,new HashSet<>(Arrays.stream(lista).map(Plato::getId).toList()),version));
    }
    DetalleContrato contrato(){return contrato(LUNES.minusDays(3),LUNES.plusDays(3));}
    DetalleContrato contrato(LocalDate inicio,LocalDate fin){
        Set<Integer> semanales=new HashSet<>(dias.findByServicio(servicio).stream().map(ServicioDiaAtencion::getDiaSemana).toList());
        Set<LocalDate> noAtiende=new HashSet<>(cierres.filtrar(localId,inicio,fin).stream().map(DiaNoAtencion::getFecha).toList());
        long cantidad=calculadora.calcular(inicio,fin,semanales,noAtiende);
        var c=new Contrato();c.setNumero("RG"+UUID.randomUUID().toString().replace("-","").substring(0,10));c.setLocalId(localId);c.setClienteId(clienteId);c.setPlanId(planId);c.setFechaInicio(inicio);c.setFechaFin(fin);c.setMontoTotal(BigDecimal.TEN.multiply(BigDecimal.valueOf(cantidad)).setScale(2));c.setEstado(inicio.isAfter(clock.fecha)?EstadoContrato.PROGRAMADO:EstadoContrato.ACTIVO);contratos.saveAndFlush(c);
        var d=new DetalleContrato();d.setContratoId(c.getId());d.setServicio(servicio);d.setRacionesContratadas(cantidad);d.setSaldoRaciones(cantidad);d.setPrecioRacionPactado(new BigDecimal("10.00"));d.setSubtotal(c.getMontoTotal());return detalles.saveAndFlush(d);
    }
    DiaNoAtencionResponseDTO cerrar(LocalDate fecha){return calendario.crear(new DiaNoAtencionRequestDTO(fecha,"Cierre de prueba"));}
    List<CompensacionRacion> reservas(DetalleContrato d){return compensaciones.findByDetalleContratoIdOrderByFechaCompensacion(d.getId());}
    ConsumoRequestDTO consumo(DetalleContrato d){return new ConsumoRequestDTO(d.getContratoId(),servicio,List.of(new PlatoCantidadRequestDTO(platoEntrada.getId(),1L),new PlatoCantidadRequestDTO(platoSegundo.getId(),1L)),false,null);}

    @Test void exigeTodasLasCategoriasAlPublicar(){
        assertTrue(assertThrows(ReglaNegocioException.class,()->programar(LUNES,platoEntrada)).getMessage().contains("Complete la programación"));
        assertTrue(programaciones.findByLocalIdAndFechaAndServicio(localId,LUNES,servicio).isEmpty());
    }
    @Test void permiteAlternativasDentroDeUnaCategoria(){
        var otro=plato("Otro segundo",segundo);assertEquals(3,programar(LUNES,platoEntrada,platoSegundo,otro).getPlatos().size());
    }
    @Test void bloqueaBajaSiElPlatoSeServiraEnUnaProgramacion(){
        programar(LUNES.plusDays(1),platoEntrada,platoSegundo);
        assertTrue(assertThrows(ReglaNegocioException.class,()->platoService.eliminar(platoSegundo.getId())).getMessage().contains("Reemplace primero"));
        assertTrue(platos.findById(platoSegundo.getId()).orElseThrow().getActivo());
    }
    @Test void bloqueaDesactivacionDesdeFormularioYReclasificacion(){
        programar(LUNES.plusDays(1),platoEntrada,platoSegundo);
        assertThrows(ReglaNegocioException.class,()->platoService.actualizar(platoSegundo.getId(),new PlatoRequestDTO("Otro nombre",segundo,false)));
        assertThrows(ReglaNegocioException.class,()->platoService.actualizar(platoSegundo.getId(),new PlatoRequestDTO("Otro nombre",entrada,true)));
    }
    @Test void permiteBajaDespuesDeReemplazarElPlato(){
        var otro=plato("Reemplazo",segundo);programar(LUNES.plusDays(1),platoEntrada,otro);platoService.eliminar(platoSegundo.getId());assertFalse(platos.findById(platoSegundo.getId()).orElseThrow().getActivo());
    }
    @Test void rechazaUnEditorConVersionAnteriorYConservaElUltimoGuardado(){
        var inicial=programar(LUNES,platoEntrada,platoSegundo);var otro=plato("Nuevo segundo",segundo);
        var nuevo=menuService.guardar(new MenuRequestDTO(LUNES,servicio,Set.of(platoEntrada.getId(),otro.getId()),inicial.getVersion()));
        assertTrue(nuevo.getVersion()>inicial.getVersion());
        assertThrows(ReglaNegocioException.class,()->menuService.guardar(new MenuRequestDTO(LUNES,servicio,Set.of(platoEntrada.getId(),platoSegundo.getId()),inicial.getVersion())));
        assertEquals(nuevo.getVersion(),programaciones.findByLocalIdAndFechaAndServicio(localId,LUNES,servicio).orElseThrow().getVersion());
    }
    @Test void unaCeldaCreadaPorOtroYaNoSePuedeGuardarComoNueva(){
        programar(LUNES,platoEntrada,platoSegundo);
        assertThrows(ReglaNegocioException.class,()->menuService.guardar(new MenuRequestDTO(LUNES,servicio,Set.of(platoEntrada.getId(),platoSegundo.getId()),-1L)));
    }
    @Test void clonarTambienCompruebaLaVersionDestino(){
        programar(LUNES,platoEntrada,platoSegundo);programar(LUNES.plusDays(1),platoEntrada,platoSegundo);
        assertThrows(ReglaNegocioException.class,()->menuService.clonar(new ClonarMenuRequestDTO(LUNES,LUNES.plusDays(1),Set.of(servicio),Map.of(servicio,-1L))));
    }
    @Test void noRegistraCierresDeHoyNiAnteriores(){
        assertThrows(DatosInvalidosException.class,()->cerrar(LUNES));assertThrows(DatosInvalidosException.class,()->cerrar(LUNES.minusDays(1)));
    }
    @Test void noMueveNiEliminaUnCierreHistorico(){
        var viejo=new DiaNoAtencion();viejo.setLocalId(localId);viejo.setFecha(LUNES.minusDays(1));viejo.setMotivo("Historico");cierres.saveAndFlush(viejo);
        assertThrows(DatosInvalidosException.class,()->calendario.actualizar(viejo.getId(),new DiaNoAtencionRequestDTO(LUNES.plusDays(1),"Cambiar")));
        assertThrows(DatosInvalidosException.class,()->calendario.eliminar(viejo.getId()));
    }
    @Test void almuerzoDelContratoQueTerminaJuevesSeCompensaViernes(){
        var d=contrato();cerrar(LUNES.plusDays(1));assertEquals(LUNES.plusDays(4),reservas(d).getFirst().getFechaCompensacion());
        assertEquals(5L,d.getSaldoRaciones());assertEquals(new BigDecimal("50.00"),d.getSubtotal());assertEquals(LUNES.plusDays(3),contratos.findById(d.getContratoId()).orElseThrow().getFechaFin());
    }
    @Test void cenaSaltaViernesYFinDeSemanaHastaLunes(){
        dias.deleteById(new ServicioDiaAtencionId(servicio,5));dias.flush();var d=contrato();cerrar(LUNES.plusDays(1));
        assertEquals(LUNES.plusDays(7),reservas(d).getFirst().getFechaCompensacion());assertEquals(4L,d.getSaldoRaciones());
    }
    @Test void noCompensaUnDiaQueYaNoEraAtendible(){
        var d=contrato(LUNES,LUNES.plusDays(6));cerrar(LUNES.plusDays(5));assertTrue(reservas(d).isEmpty());
    }
    @Test void contratosNuevosYaExcluyenElFeriadoSinCompensacionExtra(){
        cerrar(LUNES.plusDays(1));var d=contrato();assertEquals(4L,d.getRacionesContratadas());assertTrue(reservas(d).isEmpty());
    }
    @Test void variosCierresDanFechasDistintasSinDuplicarSaldo(){
        var d=contrato();cerrar(LUNES.plusDays(1));cerrar(LUNES.plusDays(2));
        assertEquals(List.of(LUNES.plusDays(4),LUNES.plusDays(7)),reservas(d).stream().map(CompensacionRacion::getFechaCompensacion).toList());assertEquals(5L,d.getSaldoRaciones());
    }
    @Test void unCierreSobreLaRecuperacionLaMueveSinAgregarOtra(){
        var d=contrato();cerrar(LUNES.plusDays(1));cerrar(LUNES.plusDays(4));
        assertEquals(1,reservas(d).size());assertEquals(LUNES.plusDays(7),reservas(d).getFirst().getFechaCompensacion());
    }
    @Test void editarMotivoNoDuplicaCompensaciones(){
        var d=contrato();var cierre=cerrar(LUNES.plusDays(1));calendario.actualizar(cierre.getId(),new DiaNoAtencionRequestDTO(cierre.getFecha(),"Otro motivo"));assertEquals(1,reservas(d).size());
    }
    @Test void eliminarCierreFuturoRetiraSuCompensacionSinCambiarSaldo(){
        var d=contrato();var cierre=cerrar(LUNES.plusDays(1));calendario.eliminar(cierre.getId());assertTrue(reservas(d).isEmpty());assertEquals(5L,d.getSaldoRaciones());
    }
    @Test void edicionDeFechaRecalculaLaCompensacionEnLaMismaTransaccion(){
        var d=contrato();var cierre=cerrar(LUNES.plusDays(1));calendario.actualizar(cierre.getId(),new DiaNoAtencionRequestDTO(LUNES.plusDays(2),"Cambio de fecha"));
        assertEquals(1,reservas(d).size());assertEquals(LUNES.plusDays(2),reservas(d).getFirst().getFechaOrigen());
    }
    @Test void sirveLaCompensacionAunqueElPeriodoOrdinarioHayaFinalizado(){
        var d=contrato();cerrar(LUNES.plusDays(1));LocalDate fecha=reservas(d).getFirst().getFechaCompensacion();programar(fecha,platoEntrada,platoSegundo);
        contratos.findById(d.getContratoId()).orElseThrow().setEstado(EstadoContrato.FINALIZADO);contratos.flush();clock.fecha=fecha;
        assertTrue(contratoService.buscarPensionistas(prefijo,localId).stream().anyMatch(p->p.getContratoId().equals(d.getContratoId())));
        var r=consumoService.crear(consumo(d));assertEquals(new BigDecimal("10.00"),r.getValorRacion());assertEquals(r.getId(),reservas(d).getFirst().getConsumoId());assertEquals(4L,d.getSaldoRaciones());
    }
    @Test void anularUnaRecuperacionDevuelveLaOportunidadDelMismoDia(){
        var d=contrato();cerrar(LUNES.plusDays(1));LocalDate fecha=reservas(d).getFirst().getFechaCompensacion();programar(fecha,platoEntrada,platoSegundo);clock.fecha=fecha;
        var r=consumoService.crear(consumo(d));consumoService.anular(r.getId(),new AnularRequestDTO("Correccion de consumo de prueba"));
        assertNull(reservas(d).getFirst().getConsumoId());assertEquals(5L,d.getSaldoRaciones());assertNotNull(consumoService.crear(consumo(d)).getId());assertEquals(4L,d.getSaldoRaciones());
    }
    @Test void noRegalaServiciosNiDiasPosterioresSinReserva(){
        var d=contrato();cerrar(LUNES.plusDays(1));clock.fecha=reservas(d).getFirst().getFechaCompensacion().plusDays(3);
        assertThrows(ReglaNegocioException.class,()->consumoService.crear(consumo(d)));assertEquals(5L,d.getSaldoRaciones());
    }
    @Test void unaRenovacionYaCobradaNoPierdeDiasPorLaCompensacion(){
        var d=contrato();var renovado=contrato(LUNES.plusDays(4),LUNES.plusDays(10));cerrar(LUNES.plusDays(1));
        assertEquals(LUNES.plusDays(11),reservas(d).getFirst().getFechaCompensacion());assertEquals(5L,renovado.getRacionesContratadas());
    }
    @Test void unaRenovacionNuevaNoCobraOtraVezLaFechaReservada(){
        var d=contrato();cerrar(LUNES.plusDays(1));
        var simulacion=contratoService.simular(new SimularContratoRequestDTO(clienteId,planId,LUNES.plusDays(4),List.of(servicio)));
        assertEquals(4,simulacion.getServicios().getFirst().getRaciones());assertEquals(new BigDecimal("40.00"),simulacion.getTotal());assertEquals(1,reservas(d).size());
    }
    @Test void cenaElLunesNoHabilitaElAlmuerzoCuyaRecuperacionEraViernes(){
        dias.deleteById(new ServicioDiaAtencionId(servicio,5));dias.flush();var cena=contrato();
        String almuerzo=servicio+"_A";var s=new Servicio();s.setCodigo(almuerzo);s.setNombre("Almuerzo prueba");servicios.saveAndFlush(s);
        for(int n=1;n<=5;n++){var d=new ServicioDiaAtencion();d.setServicio(almuerzo);d.setDiaSemana(n);dias.save(d);}dias.flush();
        var regla=new ReglaCobro();regla.setNombre("Almuerzo");regla.setServicio(almuerzo);regla.setPrecioCombo(BigDecimal.TEN);regla.setRecargoLlevar(BigDecimal.ZERO);reglas.saveAndFlush(regla);
        var d=new DetalleContrato();d.setContratoId(cena.getContratoId());d.setServicio(almuerzo);d.setRacionesContratadas(5L);d.setSaldoRaciones(5L);d.setPrecioRacionPactado(new BigDecimal("10.00"));d.setSubtotal(new BigDecimal("50.00"));detalles.saveAndFlush(d);
        var c=contratos.findById(cena.getContratoId()).orElseThrow();c.setMontoTotal(c.getMontoTotal().add(d.getSubtotal()));contratos.flush();
        cerrar(LUNES.plusDays(1));assertEquals(LUNES.plusDays(4),reservas(d).getFirst().getFechaCompensacion());assertEquals(LUNES.plusDays(7),reservas(cena).getFirst().getFechaCompensacion());
        clock.fecha=LUNES.plusDays(7);
        assertThrows(ReglaNegocioException.class,()->consumoService.crear(new ConsumoRequestDTO(c.getId(),almuerzo,List.of(new PlatoCantidadRequestDTO(platoEntrada.getId(),1L)),false,null)));
        assertEquals(5L,d.getSaldoRaciones());
    }
    @Test void moverUnFeriadoNoAgregaRacionesQueElContratoNuncaIncluyo(){
        var cierre=cerrar(LUNES.plusDays(1));var d=contrato();assertEquals(4L,d.getRacionesContratadas());
        calendario.actualizar(cierre.getId(),new DiaNoAtencionRequestDTO(LUNES.plusDays(2),"Traslado del feriado"));
        assertTrue(reservas(d).isEmpty());
    }
}
