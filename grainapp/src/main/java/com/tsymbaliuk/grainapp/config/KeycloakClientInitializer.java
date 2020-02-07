package com.tsymbaliuk.grainapp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.ResourceResource;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;

@Configuration
@Profile("keycloak-init")
public class KeycloakClientInitializer implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakClientInitializer.class.getName());

    public static final String KEYCLOAK_OIDC_JSON_PROVIDER_NAME = "keycloak-oidc-keycloak-json";
    public static final String KEYCLOAK_POLICY_ENFORCER_CONFIG_PATH =
        "classpath:keycloak/policy-enforcer-config.json";
    public static final String KEYCLOAK_CLIENT_AUTHORIZATION_CONFIG_PATH =
        "classpath:keycloak/client-authorization-config.json";
    public static final String DEFAULT_POLICY_NAME = "Default Policy";
    public static final String DEFAULT_PERMISSION_NAME = "Default Permission";
    public static final String DEFAULT_RESOURCE_NAME = "Default Resource";
    public static final String SERVICE_ROLE_NAME = "service";
    public static final String USER_ROLE_NAME = "user";
    public static final String KEYCLOAK_CLIENT_CONFIG_PATH =
        "classpath:keycloak/client-config.json";

    @Value("${keycloak.config.client-manager.id}")
    private String clientManagerId;

    @Value("${keycloak.config.client-manager.secret}")
    private String clientManagerSecret;

    @Value("${keycloak.config.url}")
    private String keycloakServerUrl;

    @Value("${keycloak.config.realm}")
    private String keycloakRealm;

    private AdapterConfig adapterConfig;

    @Override
    public void afterPropertiesSet() throws Exception {
        Keycloak keycloakAdminClient = KeycloakBuilder.builder()
            .serverUrl(keycloakServerUrl)
            .realm(keycloakRealm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientManagerId)
            .clientSecret(clientManagerSecret)
            .build();

        final ClientsResource realmClients = keycloakAdminClient.realm(keycloakRealm).clients();

        final ObjectMapper jsonObjectMapper = new ObjectMapper();
        jsonObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

        final ClientResource clientResource = ensureClientResourceExists(realmClients, jsonObjectMapper);
        logger.info("Client updated successfully");

        logger.info("Updating service roles");
        ensureServiceRolesAreCorrect(keycloakAdminClient, clientResource);

        logger.info("Updating authorization configuration");
        updateClientAuthorization(clientResource, jsonObjectMapper);

        logger.info("Client created, updated successfully");

        logger.info("Getting installation provider and configuring adapter ");
        configureClientAdapter(clientResource, jsonObjectMapper);

        logger.info("Keycloak initialization completed");
    }

    private ClientResource ensureClientResourceExists(final ClientsResource realmClients,
                                                      final ObjectMapper jsonObjectMapper) throws IOException {

//        get client representation from file
        File clientConfigFile = ResourceUtils.getFile(KEYCLOAK_CLIENT_CONFIG_PATH);
        ClientRepresentation newClientRepresentation =
            jsonObjectMapper.readValue(clientConfigFile, ClientRepresentation.class);

//        try to find client in realm
        List<ClientRepresentation> existingClients =
            realmClients.findByClientId(newClientRepresentation.getClientId());

//        try to create if client not found
        if (existingClients.isEmpty()) {
            final Response response = realmClients.create(newClientRepresentation);
            if (response.getStatusInfo().toEnum() == Response.Status.CREATED) {
                existingClients = realmClients.findByClientId(newClientRepresentation.getClientId());
            } else {
                logger.error("UNABLE TO CREATE CLIENT, keycloak response: {}", response.getStatusInfo().toEnum());
                throw new RuntimeException("UNABLE TO CREATE CLIENT");
            }
        }

//        error if client hasn't been create
        if (existingClients.isEmpty()) {
            logger.error(
                "UNABLE TO CREATE CLIENT, client doesnt exists and cant be created for unknown reason");
            throw new RuntimeException("UNABLE TO CREATE CLIENT");
        }

        logger.info("Client now exists");
        logger.info("Updating client");

//        update existing client with new representation
        final ClientRepresentation existingClient = existingClients.get(0);
        final ClientResource clientResource = realmClients.get(existingClient.getId());
        clientResource.update(newClientRepresentation);

        return clientResource;
    }

    private void ensureServiceRolesAreCorrect(final Keycloak keycloakAdminClient, final ClientResource clientResource) {

        final String serviceAccId = clientResource.getServiceAccountUser().getId();
//        all service account roles
        final List<RoleRepresentation> allAssignedServiceRoles =
            keycloakAdminClient
                .realm(keycloakRealm)
                .users()
                .get(serviceAccId)
                .roles()
                .realmLevel()
                .listAll();
//        all service account available roles
        final List<RoleRepresentation> availableServiceRoles =
            keycloakAdminClient
                .realm(keycloakRealm)
                .users()
                .get(serviceAccId)
                .roles()
                .realmLevel()
                .listAvailable();

        final Optional<RoleRepresentation> availableServiceRoleOptional =
            availableServiceRoles.stream()
                .filter(role -> SERVICE_ROLE_NAME.equals(role.getName()))
                .findFirst();
        final Optional<RoleRepresentation> assignedUserRoleOptional =
            allAssignedServiceRoles.stream()
                .filter(role -> USER_ROLE_NAME.equals(role.getName()))
                .findFirst();

//        add "service" role to realm default roles
        if (availableServiceRoleOptional.isPresent()) {
            logger.info("Adding service role into service account");
            keycloakAdminClient
                .realm(keycloakRealm)
                .users()
                .get(serviceAccId)
                .roles()
                .realmLevel()
                .add(Collections.singletonList(availableServiceRoleOptional.get()));
        }

//        remove "user" role from default roles (for security purposes)
        if (assignedUserRoleOptional.isPresent()) {
            logger.info("Removing user role from service account");
            keycloakAdminClient
                .realm(keycloakRealm)
                .users()
                .get(serviceAccId)
                .roles()
                .realmLevel()
                .remove(Collections.singletonList(assignedUserRoleOptional.get()));
        }
    }

    private void updateClientAuthorization(final ClientResource clientResource,
                                           final ObjectMapper jsonObjectMapper) throws IOException {

        removeDefaultResource(clientResource);

        removeDefaultPermission(clientResource);

        removeDefaultPolicy(clientResource);

        File authzConfigFile = ResourceUtils.getFile(KEYCLOAK_CLIENT_AUTHORIZATION_CONFIG_PATH);
        ResourceServerRepresentation authzConfigRepresentation =
            jsonObjectMapper.readValue(authzConfigFile, ResourceServerRepresentation.class);

        clientResource.authorization().importSettings(authzConfigRepresentation);
    }

    private void removeDefaultResource(final ClientResource clientResource) {
        final List<ResourceRepresentation> defaultResources =
            clientResource.authorization().resources().findByName(DEFAULT_RESOURCE_NAME);
        if (!defaultResources.isEmpty()) {
            final List<ResourceResource> defaultResourceResources =
                defaultResources.stream()
                    .map(defRes -> clientResource.authorization().resources().resource(defRes.getId()))
                    .collect(Collectors.toList());
            defaultResourceResources.forEach(ResourceResource::remove);
        }
    }

    private void removeDefaultPermission(final ClientResource clientResource) {

        final ResourcePermissionRepresentation defaultPermission =
            clientResource
                .authorization()
                .permissions()
                .resource()
                .findByName(DEFAULT_PERMISSION_NAME);

        if (Objects.nonNull(defaultPermission)) {
            clientResource
                .authorization()
                .permissions()
                .resource()
                .findById(defaultPermission.getId())
                .remove();
        }
    }

    private void removeDefaultPolicy(final ClientResource clientResource) {
        final PolicyRepresentation defaultPolicy =
            clientResource.authorization().policies().findByName(DEFAULT_POLICY_NAME);
        if (Objects.nonNull(defaultPolicy)) {
            clientResource.authorization().policies().policy(defaultPolicy.getId()).remove();
        }
    }

    private void configureClientAdapter(final ClientResource clientResource,
                                        final ObjectMapper jsonObjectMapper) throws IOException {

        final String installationProviderAdapterConfig =
            clientResource.getInstallationProvider(KEYCLOAK_OIDC_JSON_PROVIDER_NAME);

        File policyEnforcerConfigFile = ResourceUtils.getFile(KEYCLOAK_POLICY_ENFORCER_CONFIG_PATH);
        PolicyEnforcerConfig policyEnforcerConfig =
            jsonObjectMapper.readValue(policyEnforcerConfigFile, PolicyEnforcerConfig.class);

        try {
            adapterConfig =
                jsonObjectMapper.readValue(installationProviderAdapterConfig, AdapterConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create keycloak adapter config", e);
        }

        adapterConfig.setPolicyEnforcerConfig(policyEnforcerConfig);
    }

    @Bean
    public AdapterConfig adapterConfig() {
        return adapterConfig;
    }
}
