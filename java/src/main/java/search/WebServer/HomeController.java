package search.WebServer;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {
    
    /**
     * Redireciona todas as rotas que não são /api/* ou /ws/* para o index.html do React
     * Permite que o React Router funcione corretamente
     */
    @RequestMapping(value = {"", "/", "/search", "/statistics", "/index", "/backlinks"})
    public String forward() {
        return "forward:/index.html";
    }
}