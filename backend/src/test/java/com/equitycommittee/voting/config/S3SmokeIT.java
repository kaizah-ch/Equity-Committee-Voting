package com.equitycommittee.voting.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "RUN_S3_SMOKE", matches = "true")
class S3SmokeIT {

    @Test
    void canWriteReadAndDeleteSmokeObject() {
        String bucket = requiredEnv("S3_BUCKET");
        String region = envOrDefault("S3_REGION", "us-east-1");
        String key = "cases/smoke-test/" + UUID.randomUUID() + ".txt";
        String body = "equity-committee-voting-s3-smoke";

        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build()) {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromString(body, StandardCharsets.UTF_8));

            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            assertEquals(body, object.asUtf8String());

            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }
    }

    private AwsCredentialsProvider credentialsProvider() {
        String accessKey = System.getenv("AWS_ACCESS_KEY");
        String secretKey = System.getenv("AWS_SECRET_KEY");
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    private String requiredEnv(String key) {
        String value = System.getenv(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(key + " is required for S3 smoke test");
        }
        return value;
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
