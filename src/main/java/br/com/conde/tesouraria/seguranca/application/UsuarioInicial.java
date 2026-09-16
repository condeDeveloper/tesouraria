package br.com.conde.tesouraria.seguranca.application;

import br.com.conde.tesouraria.seguranca.domain.Papel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Na primeira subida, sem nenhum usuário no banco, cria o administrador inicial.
 * A senha vem de TESOURARIA_ADMIN_SENHA ou, em desenvolvimento, é "admin123".
 */
@Configuration
public class UsuarioInicial {

    private static final Logger log = LoggerFactory.getLogger(UsuarioInicial.class);

    @Bean
    ApplicationRunner criarAdminInicial(AutenticacaoService auth, Environment env) {
        return args -> {
            if (!auth.semUsuarios()) return;
            String senha = env.getProperty("TESOURARIA_ADMIN_SENHA", "admin123");
            auth.criarUsuario("admin", "Administrador", senha, Papel.ADMIN);
            log.warn("Usuário 'admin' criado. Troque a senha inicial em POST /api/auth/senha.");
        };
    }
}
