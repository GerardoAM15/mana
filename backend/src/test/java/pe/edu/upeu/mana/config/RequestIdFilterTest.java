package pe.edu.upeu.mana.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filtro = new RequestIdFilter();

    @Test
    void generaIdloDejaEnElMdcYLoDevuelve() throws Exception {
        var dentro = new AtomicReference<String>();
        var respuesta = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest(), respuesta,
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                    @Override protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                        dentro.set(MDC.get("requestId"));
                    }
                }));
        assertNotNull(dentro.get());
        assertEquals(dentro.get(), respuesta.getHeader("X-Request-Id"));
        assertNull(MDC.get("requestId"), "el MDC se limpia al terminar la petición");
    }

    @Test
    void reutilizaUnIdValidoRecibido() throws Exception {
        var peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Request-Id", "abc12345-def");
        var respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        assertEquals("abc12345-def", respuesta.getHeader("X-Request-Id"));
    }

    @Test
    void ignoraUnIdConCaracteresNoPermitidos() throws Exception {
        var peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Request-Id", "<script>\n");
        var respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        assertNotEquals("<script>\n", respuesta.getHeader("X-Request-Id"));
    }
}
