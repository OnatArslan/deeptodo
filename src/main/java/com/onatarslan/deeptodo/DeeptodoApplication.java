package com.onatarslan.deeptodo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Bu sınıfın paketi (com.onatarslan.deeptodo) component scan'in kökü: @Component / @Service / @RestController
// sınıfları yalnız bu paketin altındaysa bulunur. Bütün feature paketleri (auth, user, ...) bunun altında açılır.
@SpringBootApplication
public class DeeptodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeeptodoApplication.class, args);
    }

}
