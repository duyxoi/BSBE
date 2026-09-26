package com.ptit.backend.config;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class FirebaseAdminConfig {

    @Value("${firebase.project-id:}")
    private String firebaseProjectId;

    @Value("${firebase.service-account-path}")
    private String firebaseServiceAccountPath;

    private GoogleCredentials resolveCredentials() throws IOException{
        ClassPathResource keyPath = new ClassPathResource("firebase-auth-key.json");
        if(keyPath.exists()){
            try {
                InputStream in = keyPath.getInputStream();
                return GoogleCredentials.fromStream(in);
            } catch (Exception e) {
                e.printStackTrace();
                // TODO: handle exception
            }
        }
        throw new IllegalStateException("Khong tim thay Firebase service account JSON");
    }

    @PostConstruct
    public void init(){
        if(!FirebaseApp.getApps().isEmpty()){
            return;
        }

        try {
            FirebaseOptions.Builder builder =  FirebaseOptions.builder();
            builder.setCredentials(resolveCredentials());

            builder.setProjectId(firebaseProjectId);

            FirebaseApp.initializeApp(builder.build());
            log.info("Firebase Admin SDK initialized sucesstion");
        } catch (Exception e) {
            throw new IllegalStateException("Khong the khoi tao FirebaseConfig ",e);
            //TODO: handle exception
        }
    }
    

}
