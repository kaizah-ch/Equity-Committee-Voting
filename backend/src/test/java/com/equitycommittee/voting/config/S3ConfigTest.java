package com.equitycommittee.voting.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ConfigTest {

    @Test
    void awsBucketMustExistAndBeReachable() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(
                NoSuchBucketException.builder().message("missing").build());

        ApplicationRunner runner = config("").s3BucketInitializer(s3Client);

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void awsBucketSuccessDoesNotCreateBucket() {
        S3Client s3Client = mock(S3Client.class);
        ApplicationRunner runner = config("").s3BucketInitializer(s3Client);

        assertDoesNotThrow(() -> runner.run(null));
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void s3CompatibleEndpointCreatesMissingBucketForLocalDevelopment() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(
                NoSuchBucketException.builder().message("missing").build());

        ApplicationRunner runner = config("http://localhost:9000").s3BucketInitializer(s3Client);

        runner.run(null);
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    private S3Config config(String endpoint) {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-east-1");
        ReflectionTestUtils.setField(config, "accessKey", "access-key");
        ReflectionTestUtils.setField(config, "secretKey", "secret-key");
        ReflectionTestUtils.setField(config, "endpoint", endpoint);
        ReflectionTestUtils.setField(config, "bucket", "equity-committee-voting-images-dev");
        return config;
    }
}
