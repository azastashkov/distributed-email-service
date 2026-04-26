package com.example.email.web.attachment;

import com.example.email.common.dto.AttachmentUpload;
import com.example.email.web.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final AppProperties props;
    private S3Client s3;
    private S3Presigner internalPresigner;
    private S3Presigner publicPresigner;

    @PostConstruct
    void init() {
        var minio = props.getMinio();
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(minio.getAccessKey(), minio.getSecretKey()));
        var region = Region.of(minio.getRegion());

        s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.getEndpoint()))
                .credentialsProvider(creds)
                .region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        internalPresigner = S3Presigner.builder()
                .endpointOverride(URI.create(minio.getEndpoint()))
                .credentialsProvider(creds)
                .region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        publicPresigner = S3Presigner.builder()
                .endpointOverride(URI.create(minio.getPublicEndpoint()))
                .credentialsProvider(creds)
                .region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @PreDestroy
    void close() {
        if (s3 != null) s3.close();
        if (internalPresigner != null) internalPresigner.close();
        if (publicPresigner != null) publicPresigner.close();
    }

    public AttachmentUpload presignUpload(UUID userId, UUID emailId, String name) {
        String key = "att/" + userId + "/" + emailId + "/" + UUID.randomUUID() + "-" + safeName(name);
        Duration ttl = Duration.ofMinutes(props.getMinio().getPresignTtlMinutes());
        var presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(props.getMinio().getBucket())
                        .key(key)
                        .build())
                .build();
        String url = publicPresigner.presignPutObject(presignReq).url().toString();
        return new AttachmentUpload(name, key, url, Instant.now().plus(ttl));
    }

    public String presignDownload(String key) {
        Duration ttl = Duration.ofMinutes(props.getMinio().getPresignTtlMinutes());
        var presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(props.getMinio().getBucket())
                        .key(key)
                        .build())
                .build();
        return publicPresigner.presignGetObject(presignReq).url().toString();
    }

    public boolean headExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(props.getMinio().getBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    private static String safeName(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
