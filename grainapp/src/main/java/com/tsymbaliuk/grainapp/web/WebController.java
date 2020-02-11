package com.tsymbaliuk.grainapp.web;

import com.tsymbaliuk.grainapp.domain.Customer;
import com.tsymbaliuk.grainapp.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.resource.ProtectedResource;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import javax.ws.rs.Path;

@RestController
@RequiredArgsConstructor
public class WebController {

    private final CustomerService customerService;
    private final AuthzClient authzClient;

    @GetMapping(path = {"/simple"})
    public List<Customer> getCustomers() {
        return customerService.findAll();
    }

    @GetMapping(path = "/admin/{id}")
    public List<Customer> getCustomersForAdmin(@PathVariable String id) {
        ResourceRepresentation representation = authzClient.protection().resource().findById(id);

        return customerService.findAllWithMinAge(20);
    }

}
