package pods.project.marketplaceservice.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RequestMapping
    public String hello(){
        return "Hello From Market Place Service!";
    }
    @RequestMapping("/check")
    public String check(){
        return "I am Market-Place running at port 8082";
    }
}
