package id.my.hendisantika.k8sargocd;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-k8s-argocd
 * User: hendisantika
 * Link: s.id/hendisantika
 * Email: hendisantika@yahoo.co.id
 * Telegram : @hendisantika34
 * Date: 11/05/25
 * Time: 05.45
 * To change this template use File | Settings | File Templates.
 */
@RestController
@RequestMapping("/api/v1")
public class HelloController {

    @GetMapping(path = "/hello")
    public String sayHello() {
        return "hello from spring argo cd app";
    }
}
