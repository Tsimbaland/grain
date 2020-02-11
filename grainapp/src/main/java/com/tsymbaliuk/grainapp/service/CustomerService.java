package com.tsymbaliuk.grainapp.service;

import com.tsymbaliuk.grainapp.domain.Customer;
import com.tsymbaliuk.grainapp.repositories.CustomerDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerDAO customerDAO;

    @PostConstruct
    public void addCustomers() {

        Customer customer1 = new Customer();
        customer1.setAddress("1111 foo blvd");
        customer1.setName("Foo Industries");
        customer1.setServiceRendered("Important services");
        customer1.setAge(18);
        customerDAO.save(customer1);

        Customer customer2 = new Customer();
        customer2.setAddress("2222 bar street");
        customer2.setName("Bar LLP");
        customer2.setServiceRendered("Important services");
        customer2.setAge(33);
        customerDAO.save(customer2);

        Customer customer3 = new Customer();
        customer3.setAddress("33 main street");
        customer3.setName("Big LLC");
        customer3.setServiceRendered("Important services");
        customer3.setAge(50);
        customerDAO.save(customer3);
    }

    public List<Customer> findAll() {
        final Iterable<Customer> iterable = customerDAO.findAll();
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }

    public List<Customer> findAllWithMinAge(int minAge) {
        Iterable<Customer> customers = customerDAO.findAll();
        return StreamSupport.stream(customers.spliterator(), false)
            .filter(customer -> customer.getAge() >= minAge)
            .collect(Collectors.toList());
    }

}
