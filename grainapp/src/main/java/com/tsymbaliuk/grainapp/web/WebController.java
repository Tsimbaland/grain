package com.tsymbaliuk.grainapp.web;

import com.tsymbaliuk.grainapp.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final CustomerService customerService;

    @GetMapping(path = "/")
    public String index() {
        return "external";
    }

    @GetMapping(path = "/customers")
    public String customers(Principal principal, Model model) {
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("username", principal.getName());
        return "customers";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) throws ServletException {
        request.logout();
        return "external";
    }

}
