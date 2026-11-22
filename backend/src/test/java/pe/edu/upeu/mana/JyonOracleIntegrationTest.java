package pe.edu.upeu.mana;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import pe.edu.upeu.mana.entity.*;
import pe.edu.upeu.mana.repository.*;
import pe.edu.upeu.mana.service.service.ContextoLocalService;
import pe.edu.upeu.mana.service.CalculadoraRaciones;
import pe.edu.upeu.mana.enums.PeriodoPlan;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** API real, filtros de seguridad y Oracle. Cada prueba revierte sus datos. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.facturacion.habilitada=false", "app.admin.password="})
@EnabledIfEnvironmentVariable(named="MANA_ORACLE_TEST", matches="true")
@Transactional
class JyonOracleIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired JsonMapper json;
    @Autowired LocalRepository locales;
    @Autowired ServicioRepository servicios;
    @Autowired ServicioDiaAtencionRepository dias;
    @Autowired CategoriaPlatoRepository categorias;
    @Autowired PlatoRepository platos;
    @Autowired ReglaCobroRepository reglas;
    @Autowired ComponenteReglaRepository componentes;
    @Autowired CalculadoraRaciones calculadora;
    @MockitoBean ContextoLocalService local;
    MockMvc mvc;
    Long localId;
    String servicio, categoria, prefijo;
    final LocalDate lunes=LocalDate.of(2030,1,1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    final String[] permisos={"MENU_LEER","MENU_GESTIONAR","PLAN_GESTIONAR","CALENDARIO_GESTIONAR"};

    @BeforeEach void preparar() {
        mvc=webAppContextSetup(context).apply(springSecurity()).build();
        prefijo="QA_JYON_"+UUID.randomUUID().toString().substring(0,8);
        var l=new Local();l.setNombre(prefijo);localId=locales.saveAndFlush(l).getId();
        when(local.localId(null)).thenReturn(localId);
        servicio=prefijo;var s=new Servicio();s.setCodigo(servicio);s.setNombre(prefijo);servicios.saveAndFlush(s);
        for(int i=1;i<=5;i++){var d=new ServicioDiaAtencion();d.setServicio(servicio);d.setDiaSemana(i);dias.save(d);}dias.flush();
        categoria=prefijo;var c=new CategoriaPlato();c.setCodigo(categoria);c.setNombre(prefijo);categorias.saveAndFlush(c);
    }
    ResultActions request(String method,String path,Object body,String... authorities)throws Exception{
        var req=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
        if(authorities!=null)req.with(user("qa-jyon").authorities(Arrays.stream(authorities).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList()));
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }
    JsonNode ok(String method,String path,Object body,int status)throws Exception{
        var r=request(method,path,body,permisos).andReturn().getResponse();
        assertEquals(status,r.getStatus(),path+" => "+r.getContentAsString());
        return r.getContentAsString().isBlank()?null:json.readTree(r.getContentAsString());
    }
    Map<String,Object> plato(String nombre,String cat,boolean activo){return Map.of("nombre",nombre,"categoria",cat,"activo",activo);}
    long crearPlato(String nombre,String cat)throws Exception{return ok("POST","/api/v1/platos",plato(prefijo+nombre,cat,true),201).get("id").asLong();}
    Map<String,Object> menu(LocalDate fecha,String s,Long... ids){
        // Cada turno de prueba tiene una composición explícita, igual que en producción.
        if(s.equals(servicio) && reglas.findByServicioAndActivoTrueOrderById(s).isEmpty()) {
            var r=new ReglaCobro();r.setNombre(prefijo+"Composición");r.setServicio(s);r.setPrecioCombo(new BigDecimal("12.00"));r.setRecargoLlevar(BigDecimal.ZERO);reglas.saveAndFlush(r);
            var c=new ComponenteRegla();c.setReglaId(r.getId());c.setCategoria(categoria);c.setCantidad(1L);componentes.saveAndFlush(c);
        }
        return Map.of("fecha",fecha.toString(),"servicio",s,"platoIds",List.of(ids));
    }
    Map<String,Object> regla(boolean activo){return Map.of("nombre",prefijo,"servicio",servicio,"precioCombo",12.50,"recargoLlevar",2,"activo",activo,"componentes",List.of(Map.of("categoria",categoria,"cantidad",1)));}

    @Test void cu11AltaEdicionConsultaYBajaLogica()throws Exception{
        long id=crearPlato("Inicial",categoria);
        assertEquals(categoria,ok("GET","/api/v1/platos/"+id,null,200).get("categoria").asText());
        ok("PUT","/api/v1/platos/"+id,plato(prefijo+"Editado",categoria,true),200);
        assertEquals(prefijo+"Editado",platos.findById(id).orElseThrow().getNombre());
        ok("DELETE","/api/v1/platos/"+id,null,204);
        assertFalse(platos.findById(id).orElseThrow().getActivo());
    }
    @Test void cu11RechazaCategoriaInactiva()throws Exception{
        var c=categorias.findById(categoria).orElseThrow();c.setActivo(false);categorias.flush();
        ok("POST","/api/v1/platos",plato(prefijo,categoria,true),409);
    }
    @Test void cu11RechazaCategoriaInexistente()throws Exception{ok("POST","/api/v1/platos",plato(prefijo,"NO_EXISTE_QA",true),409);}
    @Test void cu11RechazaDuplicadoIgnorandoMayusculasYEspacios()throws Exception{
        crearPlato(" Arroz con pollo",categoria);
        ok("POST","/api/v1/platos",plato("  "+prefijo.toLowerCase()+"   arroz  con  pollo  ",categoria,true),409);
    }
    @Test void cu11EditarElMismoNombreNoEsDuplicado()throws Exception{
        long id=crearPlato("Igual",categoria);ok("PUT","/api/v1/platos/"+id,plato(prefijo+"Igual",categoria,true),200);
    }
    @Test void cu11EditarNoPuedeDuplicarOtroPlato()throws Exception{
        crearPlato("Existente",categoria);long id=crearPlato("Otro",categoria);
        ok("PUT","/api/v1/platos/"+id,plato(prefijo+"Existente",categoria,true),409);
    }
    @Test void cu11NoDuplicaUnPlatoInactivoYPermiteReactivarlo()throws Exception{
        long id=crearPlato("Inactivo",categoria);ok("DELETE","/api/v1/platos/"+id,null,204);
        ok("PUT","/api/v1/platos/"+id,plato(prefijo+"Inactivo",categoria,true),200);
        assertTrue(platos.findById(id).orElseThrow().getActivo());
    }
    @Test void cu11PlatoInactivoTambienImpideDuplicado()throws Exception{
        long id=crearPlato("Inactivo",categoria);ok("DELETE","/api/v1/platos/"+id,null,204);
        ok("POST","/api/v1/platos",plato(prefijo+"Inactivo",categoria,true),409);
    }
    @Test void cu11MismoNombreEnOtraCategoriaEsValido()throws Exception{
        crearPlato("Versatil",categoria);crearPlato("Versatil","SEGUNDO");
    }
    @Test void cu11IndiceOracleImpideDuplicarSinPasarPorElServicio()throws Exception{
        crearPlato("Directo",categoria);var p=new Plato();p.setNombre(prefijo.toUpperCase()+"DIRECTO");p.setCategoria(categoria);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->platos.saveAndFlush(p));
    }
    @Test void cu12GuardaYReemplazaLaCeldaSinDuplicarla()throws Exception{
        long a=crearPlato("A",categoria),b=crearPlato("B",categoria);
        var creado=ok("PUT","/api/v1/menus",menu(lunes,servicio,a),200);
        var editado=ok("PUT","/api/v1/menus",menu(lunes,servicio,b),200);
        assertEquals(creado.get("id"),editado.get("id"));assertEquals(1,editado.get("platos").size());
        assertEquals(b,editado.get("platos").get(0).get("platoId").asLong());
        assertEquals(1,ok("GET","/api/v1/menus?desde="+lunes+"&hasta="+lunes+"&servicio="+servicio,null,200).size());
    }
    @Test void cu12RechazaPlatoInactivo()throws Exception{
        long id=crearPlato("Inactivo",categoria);ok("DELETE","/api/v1/platos/"+id,null,204);
        ok("PUT","/api/v1/menus",menu(lunes,servicio,id),409);
    }
    @Test void cu12RechazaPlatoInexistente()throws Exception{ok("PUT","/api/v1/menus",menu(lunes,servicio,Long.MAX_VALUE),409);}
    @Test void cu12RechazaProgramarSinComposicionActiva()throws Exception{
        long id=crearPlato("SinRegla",categoria);ok("PUT","/api/v1/menus",Map.of("fecha",lunes.toString(),"servicio",servicio,"platoIds",List.of(id)),409);
    }
    @Test void cu12RechazaCategoriasAjenasAlTurno()throws Exception{
        long id=crearPlato("Segundo","SEGUNDO");ok("PUT","/api/v1/menus",menu(lunes,servicio,id),409);
    }
    @Test void cu12PermiteVariasAlternativasDeLaMismaCategoria()throws Exception{
        long a=crearPlato("OpcionA",categoria),b=crearPlato("OpcionB",categoria);
        assertEquals(2,ok("PUT","/api/v1/menus",menu(lunes,servicio,a,b),200).get("platos").size());
    }
    @Test void cu12RechazaDiaSinAtencion()throws Exception{long id=crearPlato("FinSemana",categoria);ok("PUT","/api/v1/menus",menu(lunes.plusDays(5),servicio,id),409);}
    @Test void cu12ClonaSoloLoSolicitado()throws Exception{
        long id=crearPlato("Clon",categoria);ok("PUT","/api/v1/menus",menu(lunes,servicio,id),200);
        var clon=ok("POST","/api/v1/menus/clonar",Map.of("fechaOrigen",lunes.toString(),"fechaDestino",lunes.plusDays(1).toString(),"servicios",List.of(servicio)),200);
        assertEquals(1,clon.size());assertEquals(id,clon.get(0).get("platos").get(0).get("platoId").asLong());
    }
    @Test void cu12ClonarOmiteDestinoCerrado()throws Exception{
        long id=crearPlato("ClonCerrado",categoria);ok("PUT","/api/v1/menus",menu(lunes,servicio,id),200);
        var clon=ok("POST","/api/v1/menus/clonar",Map.of("fechaOrigen",lunes.toString(),"fechaDestino",lunes.plusDays(5).toString(),"servicios",List.of(servicio)),200);assertEquals(0,clon.size());
    }
    @Test void cu12NoClonaSobreLaMismaFecha()throws Exception{ok("POST","/api/v1/menus/clonar",Map.of("fechaOrigen",lunes.toString(),"fechaDestino",lunes.toString()),400);}
    @Test void cu12ClonarSinOrigenDevuelve404()throws Exception{ok("POST","/api/v1/menus/clonar",Map.of("fechaOrigen",lunes.toString(),"fechaDestino",lunes.plusDays(1).toString()),404);}
    @Test void cu12CopiarACenaConservaExistentesYNoDuplicaSegundos()throws Exception{
        long segundo=crearPlato("Segundo","SEGUNDO"),entrada=crearPlato("Entrada","ENTRADA"),otro=crearPlato("Otro","SEGUNDO");
        ok("PUT","/api/v1/menus",menu(lunes,"A",segundo,entrada),200);ok("PUT","/api/v1/menus",menu(lunes,"C",otro),200);
        var copia=ok("POST","/api/v1/menus/copiar-a-cena",Map.of("fecha",lunes.toString()),200);assertEquals(2,copia.get("platos").size());
        var repetida=ok("POST","/api/v1/menus/copiar-a-cena",Map.of("fecha",lunes.toString()),200);assertEquals(2,repetida.get("platos").size());
        var ids=new HashSet<Long>();repetida.get("platos").forEach(p->ids.add(p.get("platoId").asLong()));assertEquals(Set.of(segundo,otro),ids);
    }
    @Test void cu13AltaComposicionYEdicion()throws Exception{
        var r=ok("POST","/api/v1/reglas-cobro",regla(true),201);assertEquals(1,r.get("componentes").size());
        var edit=new HashMap<>(regla(true));edit.put("precioCombo",14);edit.put("componentes",List.of(Map.of("categoria",categoria,"cantidad",2)));
        var nuevo=ok("PUT","/api/v1/reglas-cobro/"+r.get("id").asLong(),edit,200);assertEquals(2,nuevo.get("componentes").get(0).get("cantidad").asInt());assertEquals(14,nuevo.get("precioCombo").asInt());
    }
    @Test void cu13RechazaSegundoMenuActivo()throws Exception{ok("POST","/api/v1/reglas-cobro",regla(true),201);var r=new HashMap<>(regla(true));r.put("nombre",prefijo+"Otro");ok("POST","/api/v1/reglas-cobro",r,409);}
    @Test void cu13PermiteReemplazoTrasDesactivar()throws Exception{
        var primero=ok("POST","/api/v1/reglas-cobro",regla(true),201);ok("DELETE","/api/v1/reglas-cobro/"+primero.get("id").asLong(),null,204);
        var r=new HashMap<>(regla(true));r.put("nombre",prefijo+"Nuevo");ok("POST","/api/v1/reglas-cobro",r,201);
    }
    @Test void cu13RechazaCategoriaRepetida()throws Exception{var r=new HashMap<>(regla(true));r.put("componentes",List.of(Map.of("categoria",categoria,"cantidad",1),Map.of("categoria",categoria,"cantidad",2)));ok("POST","/api/v1/reglas-cobro",r,400);}
    @Test void cu14CrearActualizarYDesactivarTarifa()throws Exception{
        var dto=Map.of("servicio",servicio,"precio",3.20,"activo",true);var t=ok("POST","/api/v1/tarifas",dto,201);
        var editado=ok("PUT","/api/v1/tarifas/"+t.get("id").asLong(),Map.of("servicio",servicio,"precio",4.50,"activo",true),200);assertEquals(0,new BigDecimal("4.50").compareTo(editado.get("precio").decimalValue()));
        ok("DELETE","/api/v1/tarifas/"+t.get("id").asLong(),null,204);assertFalse(ok("GET","/api/v1/tarifas/"+t.get("id").asLong(),null,200).get("activo").asBoolean());
    }
    @Test void cu14TarifaDuplicadaDevuelveConflicto()throws Exception{var dto=Map.of("servicio",servicio,"precio",3.20,"activo",true);ok("POST","/api/v1/tarifas",dto,201);ok("POST","/api/v1/tarifas",dto,409);}
    @Test void cu14PrecioSueltoCRUDYClaveInmutable()throws Exception{
        var dto=Map.of("servicio",servicio,"categoria",categoria,"precio",5,"recargoLlevar",1,"activo",true);ok("POST","/api/v1/precios-sueltos",dto,201);
        var edit=new HashMap<>(dto);edit.put("precio",6);ok("PUT","/api/v1/precios-sueltos/"+servicio+"/"+categoria,edit,200);
        ok("DELETE","/api/v1/precios-sueltos/"+servicio+"/"+categoria,null,204);
    }
    @Test void cu14NoPermiteCambiarClaveDelPrecio()throws Exception{ok("PUT","/api/v1/precios-sueltos/"+servicio+"/"+categoria,Map.of("servicio","OTRO","categoria",categoria,"precio",5,"recargoLlevar",0,"activo",true),400);}
    @Test void cu14RegresionCenaNoDebePermitirPreciosSueltos()throws Exception{
        // RF/documentación: en la cena solo se vende el menú completo.
        ok("POST","/api/v1/precios-sueltos",Map.of("servicio","C","categoria",categoria,"precio",5,"recargoLlevar",0,"activo",true),409);
    }
    @Test void cu22CrearEditarVinculosYDesactivarPlan()throws Exception{
        var dto=Map.of("nombre",prefijo,"periodo","SEMANAL","activo",true,"servicios",List.of(servicio));var p=ok("POST","/api/v1/planes",dto,201);long id=p.get("id").asLong();
        assertEquals(servicio,p.get("servicios").get(0).asText());var edit=new HashMap<>(dto);edit.put("periodo","MENSUAL");edit.put("servicios",List.of("A","D"));
        var modificado=ok("PUT","/api/v1/planes/"+id,edit,200);assertEquals("MENSUAL",modificado.get("periodo").asText());assertEquals(2,modificado.get("servicios").size());
        ok("DELETE","/api/v1/planes/"+id,null,204);assertFalse(ok("GET","/api/v1/planes/"+id,null,200).get("activo").asBoolean());
    }
    @Test void cu22RechazaServicioInexistente()throws Exception{ok("POST","/api/v1/planes",Map.of("nombre",prefijo,"periodo","SEMANAL","activo",true,"servicios",List.of("NO_EXISTE_QA")),409);}
    @Test void cu23CierreBloqueaProgramacionYEliminarRestauraAtencion()throws Exception{
        long id=crearPlato("Cierre",categoria);var cierre=ok("POST","/api/v1/dias-no-atencion",Map.of("fecha",lunes.toString(),"motivo",prefijo),201);
        // La petición fallida marca rollback-only: verificar el bloqueo en otro test.
        assertEquals(localId.longValue(),cierre.get("localId").asLong());
        ok("PUT","/api/v1/dias-no-atencion/"+cierre.get("id").asLong(),Map.of("fecha",lunes.plusDays(1).toString(),"motivo",prefijo+"editado"),200);
        ok("PUT","/api/v1/menus",menu(lunes,servicio,id),200);ok("DELETE","/api/v1/dias-no-atencion/"+cierre.get("id").asLong(),null,204);
        ok("PUT","/api/v1/menus",menu(lunes.plusDays(1),servicio,id),200);
    }
    @Test void cu23RechazaProgramacionEnCierre()throws Exception{long id=crearPlato("Cerrado",categoria);ok("POST","/api/v1/dias-no-atencion",Map.of("fecha",lunes.toString(),"motivo",prefijo),201);ok("PUT","/api/v1/menus",menu(lunes,servicio,id),409);}
    @Test void cu23FechaDuplicadaDevuelve409()throws Exception{var dto=Map.of("fecha",lunes.toString(),"motivo",prefijo);ok("POST","/api/v1/dias-no-atencion",dto,201);ok("POST","/api/v1/dias-no-atencion",dto,409);}
    @Test void cu23CalculaRacionesConCierreYPeriodoMensualBisiesto(){assertEquals(4,calculadora.calcular(lunes,lunes.plusDays(6),Set.of(1,2,3,4,5),Set.of(lunes.plusDays(2))));assertEquals(LocalDate.of(2032,2,29),calculadora.fechaFin(LocalDate.of(2032,2,1),PeriodoPlan.MENSUAL));}

    static Stream<Arguments> entradasInvalidas(){return Stream.of(
        Arguments.of("POST","/api/v1/platos","{\"nombre\":\" \",\"categoria\":\"SEGUNDO\",\"activo\":true}"),
        Arguments.of("POST","/api/v1/platos","{\"nombre\":\"A\",\"categoria\":\"\",\"activo\":true}"),
        Arguments.of("PUT","/api/v1/menus","{\"fecha\":\"2030-01-07\",\"servicio\":\"A\",\"platoIds\":[]}"),
        Arguments.of("PUT","/api/v1/menus","{\"fecha\":\"2030-02-30\",\"servicio\":\"A\",\"platoIds\":[1]}"),
        Arguments.of("PUT","/api/v1/menus","{\"fecha\":\"2030-01-07\",\"servicio\":\"A\",\"platoIds\":[-1]}"),
        Arguments.of("POST","/api/v1/tarifas","{\"servicio\":\"A\",\"precio\":0,\"activo\":true}"),
        Arguments.of("POST","/api/v1/tarifas","{\"servicio\":\"A\",\"precio\":1.234,\"activo\":true}"),
        Arguments.of("POST","/api/v1/reglas-cobro","{\"nombre\":\"A\",\"servicio\":\"A\",\"precioCombo\":12,\"recargoLlevar\":-1,\"activo\":true,\"componentes\":[{\"categoria\":\"SEGUNDO\",\"cantidad\":1}]}"),
        Arguments.of("POST","/api/v1/reglas-cobro","{\"nombre\":\"A\",\"servicio\":\"A\",\"precioCombo\":12,\"recargoLlevar\":0,\"activo\":true,\"componentes\":[]}"),
        Arguments.of("POST","/api/v1/reglas-cobro","{\"nombre\":\"A\",\"servicio\":\"A\",\"precioCombo\":12,\"recargoLlevar\":0,\"activo\":true,\"componentes\":[{\"categoria\":\"SEGUNDO\",\"cantidad\":1.5}]}"),
        Arguments.of("POST","/api/v1/planes","{\"nombre\":\"A\",\"periodo\":\"SEMANAL\",\"activo\":true,\"servicios\":[]}"),
        Arguments.of("POST","/api/v1/planes","{\"nombre\":\"A\",\"periodo\":\"ANUAL\",\"activo\":true,\"servicios\":[\"A\"]}"),
        Arguments.of("POST","/api/v1/dias-no-atencion","{\"fecha\":\"2030-01-07\",\"motivo\":\" \"}"),
        Arguments.of("POST","/api/v1/dias-no-atencion","{\"fecha\":\"invalida\",\"motivo\":\"Cierre\"}")
    );}
    @ParameterizedTest @MethodSource("entradasInvalidas") void validacionesHttp(String method,String path,String body)throws Exception{
        var req=method.equals("PUT")?put(path):post(path);var r=mvc.perform(req.with(user("qa").authorities(Arrays.stream(permisos).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList())).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();assertEquals(400,r.getStatus(),body+" => "+r.getContentAsString());
    }
    @ParameterizedTest @ValueSource(strings={"platos","menus","reglas-cobro","tarifas","precios-sueltos","planes","dias-no-atencion"})
    void anonimoNoConsulta(String ruta)throws Exception{assertEquals(401,request("GET","/api/v1/"+ruta,null,(String[])null).andReturn().getResponse().getStatus());}
    @ParameterizedTest @ValueSource(strings={"platos","menus","reglas-cobro","tarifas","precios-sueltos"})
    void lectorPuedeConsultarMenu(String ruta)throws Exception{assertEquals(200,request("GET","/api/v1/"+ruta,null,"MENU_LEER").andReturn().getResponse().getStatus());}
    @ParameterizedTest @ValueSource(strings={"platos","reglas-cobro","tarifas","precios-sueltos","planes","dias-no-atencion"})
    void lectorNoPuedeCrear(String ruta)throws Exception{assertEquals(403,request("POST","/api/v1/"+ruta,Map.of(),"MENU_LEER").andReturn().getResponse().getStatus());}
    @ParameterizedTest @ValueSource(strings={"planes","dias-no-atencion"})
    void lectorNoAccedeGestion(String ruta)throws Exception{assertEquals(403,request("GET","/api/v1/"+ruta,null,"MENU_LEER").andReturn().getResponse().getStatus());}
}
