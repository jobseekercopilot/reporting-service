package com.jobseekercopilot.reporting.config;

import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DownstreamApiConfig {
    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    UserProfilesApi userProfilesApi(@Value("${services.user-profile-service.base-url}") String baseUrl) {
        var client = new com.jobseekercopilot.generated.userprofileservice.client.ApiClient();
        client.setBasePath(baseUrl);
        return new UserProfilesApi(client);
    }
}
