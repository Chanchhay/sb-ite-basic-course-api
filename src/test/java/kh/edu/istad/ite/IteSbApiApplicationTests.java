package kh.edu.istad.ite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole bean graph against src/test/resources/application-test.yaml,
 * which supplies stand-in values for every external system. It proves the
 * wiring holds together; it deliberately talks to nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
class IteSbApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
