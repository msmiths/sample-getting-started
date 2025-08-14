/*******************************************************************************
 * Copyright (c) 2017, 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/

package io.openliberty.sample.system;

import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@RequestScoped
@Path("/properties")
public class SystemResource {

    @Inject
    SystemConfig systemConfig;

    SecretsManagerClient secretsManager;

    public SystemResource() {
        secretsManager = SecretsManagerClient.builder()
            .region(Region.of("us-east-1"))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed(name = "getPropertiesTime", description = "Time needed to get the properties of a system")
    @Counted(absolute = true, description = "Number of times the properties of a systems is requested")
    public Response getProperties() {
        if (!systemConfig.isInMaintenance()) {
            var properties = System.getProperties();

            ListSecretsRequest listRequest = ListSecretsRequest.builder()
                .maxResults(100)
                .build();
            ListSecretsResponse valueResponse = secretsManager.listSecrets(listRequest);

            var secrets = valueResponse.secretList();
            for (SecretListEntry secret : secrets) {
                var name = secret.name();
                var value = getSecret(secret.arn());
                if (value != null) {
                    properties.put(name, value);
                } else {
                    properties.put(name, "null");
                }
            } // FOR

            return Response.ok(properties).build();
        } else {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("ERROR: Service is currently in maintenance.")
                .build();
        }
    }

    private String getSecret(String arn) {
        String secretValue = null;
        try {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                .secretId(arn)
                .build();
            GetSecretValueResponse valueResponse = secretsManager.getSecretValue(valueRequest);
            secretValue = valueResponse.secretString();
        } catch (SecretsManagerException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
        }

        return secretValue;
    }
}
