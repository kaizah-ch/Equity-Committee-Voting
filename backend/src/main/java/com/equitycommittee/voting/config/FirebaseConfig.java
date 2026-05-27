package com.equitycommittee.voting.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(
            @Value("${app.firebase.service-account-path}") String serviceAccountPath
    ) throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            try (FileInputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
            }
        }
        return FirebaseMessaging.getInstance();
    }
}
