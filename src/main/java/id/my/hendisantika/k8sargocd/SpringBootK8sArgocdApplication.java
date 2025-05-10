package id.my.hendisantika.k8sargocd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class SpringBootK8sArgocdApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootK8sArgocdApplication.class, args);
    }

    @Bean
    ApplicationRunner runner() {
        return args -> {
            log.info("Application Version: V2");
        };
    }
}
