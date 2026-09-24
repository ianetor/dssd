package com.dssd.backend.services;

import com.dssd.backend.repositories.MunicipioRepository;
import com.dssd.backend.repositories.OngRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthServiceTest {

    private final AuthService authService = new AuthService(
            "http://localhost:8081/bonita",
            mock(MunicipioRepository.class),
            mock(OngRepository.class));

    @Test
    void normalizaRepresentanteDeOngSinConfundirloConOperadorMunicipal() throws Exception {
        Object rol = normalizarRol("representante_ong", "Representante de ONG");

        assertThat(campoRol(rol, "rolCodigo")).isEqualTo("REPRESENTANTE_ONG");
        assertThat(campoRol(rol, "rolNombre")).isEqualTo("Representante de ONG");
    }

    @Test
    void normalizaOperadorMunicipalSinUsarElRolPorDefecto() throws Exception {
        Object rol = normalizarRol("operador_municipal", "Operador Municipal");

        assertThat(campoRol(rol, "rolCodigo")).isEqualTo("OPERADOR_MUNICIPAL");
        assertThat(campoRol(rol, "rolNombre")).isEqualTo("Operador Municipal");
    }

    @Test
    void normalizaLaCategoriaCuandoVieneEnElGrupoDeBonita() throws Exception {
        Object rolMunicipal = normalizarRol("user", "Municipalidad de Rosario");
        Object rolOng = normalizarRol("user", "Grupo Representante ONG");

        assertThat(campoRol(rolMunicipal, "rolCodigo")).isEqualTo("OPERADOR_MUNICIPAL");
        assertThat(campoRol(rolOng, "rolCodigo")).isEqualTo("REPRESENTANTE_ONG");
    }

    private Object normalizarRol(String name, String displayName) throws Exception {
        Method method = AuthService.class.getDeclaredMethod(
                "normalizarRol", String.class, String.class);
        method.setAccessible(true);
        return method.invoke(authService, name, displayName);
    }

    private String campoRol(Object rol, String nombre) throws Exception {
        Method accessor = rol.getClass().getDeclaredMethod(nombre);
        accessor.setAccessible(true);
        return (String) accessor.invoke(rol);
    }
}
