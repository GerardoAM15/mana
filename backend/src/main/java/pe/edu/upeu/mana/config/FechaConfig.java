package pe.edu.upeu.mana.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FechaConfig implements WebMvcConfigurer {

    @Bean
    Clock clock(@Value("${app.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatterForFieldType(LocalDate.class, new Formatter<LocalDate>() {
            @Override
            public LocalDate parse(String texto, Locale locale) {
                return LocalDate.parse(texto, DateTimeFormatter.ISO_LOCAL_DATE);
            }

            @Override
            public String print(LocalDate fecha, Locale locale) {
                return DateTimeFormatter.ISO_LOCAL_DATE.format(fecha);
            }
        });
    }
}
