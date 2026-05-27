package com.equitycommittee.voting.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {
    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Value("${app.s3.region}") private String region;
    @Value("${app.s3.access-key}") private String accessKey;
    @Value("${app.s3.secret-key}") private String secretKey;
    @Value("${app.s3.endpoint}") private String endpoint;
    @Value("${app.s3.bucket}") private String bucket;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider());
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return builder.build();
    }

    @Bean
    public ApplicationRunner s3BucketInitializer(S3Client s3Client) {
        return args -> {
            if (!StringUtils.hasText(bucket)) {
                log.warn("S3 bucket is not configured; image uploads will fail until S3_BUCKET is set");
                return;
            }

            boolean s3CompatibleEndpoint = StringUtils.hasText(endpoint);
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                log.info("Verified S3 bucket {}", bucket);
            } catch (NoSuchBucketException ex) {
                if (s3CompatibleEndpoint) {
                    createS3CompatibleBucket(s3Client);
                    return;
                }
                throw new IllegalStateException("Configured AWS S3 bucket does not exist or is not accessible: " + bucket, ex);
            } catch (S3Exception ex) {
                if (s3CompatibleEndpoint) {
                    if (ex.statusCode() == 404) {
                        createS3CompatibleBucket(s3Client);
                        return;
                    }
                    log.warn("Could not verify S3-compatible bucket {} at {}: {}", bucket, endpoint, ex.getMessage());
                    return;
                }
                throw new IllegalStateException(
                        "Could not verify AWS S3 bucket " + bucket
                                + " in region " + region
                                + " (status " + ex.statusCode() + "). "
                                + "Check S3_REGION and make sure the root .env is loaded when running locally: "
                                + safeMessage(ex),
                        ex);
            } catch (RuntimeException ex) {
                if (s3CompatibleEndpoint) {
                    log.warn("Could not reach S3-compatible endpoint {} for bucket {}: {}", endpoint, bucket, ex.getMessage());
                    return;
                }
                throw new IllegalStateException("Could not reach AWS S3 bucket " + bucket + ": " + ex.getMessage(), ex);
            }
        };
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider());
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    private void createS3CompatibleBucket(S3Client s3Client) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        log.info("Created S3-compatible bucket {}", bucket);
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? "no provider message" : ex.getMessage();
    }
}
