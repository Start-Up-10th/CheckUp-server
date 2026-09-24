package com.checkup.checkup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CheckupApplicationTests {

    @Test
    void contextLoads() {
    }

}
