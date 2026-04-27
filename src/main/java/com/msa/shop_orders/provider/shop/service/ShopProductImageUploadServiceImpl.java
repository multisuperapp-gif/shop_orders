package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.FileEntity;
import com.msa.shop_orders.persistence.repository.FileRepository;
import com.msa.shop_orders.provider.shop.dto.ShopImageUploadData;
import com.msa.shop_orders.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class ShopProductImageUploadServiceImpl implements ShopProductImageUploadService {
    private static final String STORAGE_PROVIDER_LOCAL = "LOCAL";
    private static final String STORAGE_PROVIDER_S3 = "S3";

    private final FileRepository fileRepository;
    private final CurrentUserService currentUserService;
    private final ShopMediaStorageProperties shopMediaStorageProperties;
    private volatile S3Client s3Client;

    public ShopProductImageUploadServiceImpl(
            FileRepository fileRepository,
            CurrentUserService currentUserService,
            ShopMediaStorageProperties shopMediaStorageProperties
    ) {
        this.fileRepository = fileRepository;
        this.currentUserService = currentUserService;
        this.shopMediaStorageProperties = shopMediaStorageProperties;
    }

    @Override
    public ShopImageUploadData upload(String assetType, MultipartFile file) {
        ImageAssetRule rule = ImageAssetRule.from(assetType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Image file is required.", HttpStatus.BAD_REQUEST);
        }
        String mimeType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!rule.isMimeTypeAllowed(mimeType)) {
            throw new BusinessException("UNSUPPORTED_IMAGE_TYPE", "Only JPG or PNG images are allowed for this section.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > rule.maxBytes()) {
            throw new BusinessException("IMAGE_TOO_LARGE", "Image file is larger than the allowed limit for this section.", HttpStatus.BAD_REQUEST);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("FILE_READ_FAILED", "Unable to read image file.", HttpStatus.BAD_REQUEST);
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new BusinessException("IMAGE_PARSE_FAILED", "Unable to process image file.", HttpStatus.BAD_REQUEST);
        }
        if (image == null) {
            throw new BusinessException("IMAGE_PARSE_FAILED", "Unsupported image format.", HttpStatus.BAD_REQUEST);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        rule.validateDimensions(width, height);

        String originalFilename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "product-image"
                : file.getOriginalFilename();
        String safeName = sanitizeFilename(originalFilename);
        String objectKey = buildObjectKey(rule.objectKeyPrefix(shopMediaStorageProperties), safeName);
        String storageProvider = useS3() ? STORAGE_PROVIDER_S3 : STORAGE_PROVIDER_LOCAL;
        storeBytes(shopMediaStorageProperties.getPublicMedia().getBucket(), objectKey, bytes, mimeType, storageProvider);

        FileEntity entity = new FileEntity();
        entity.setStorageProvider(storageProvider);
        entity.setBucketName(shopMediaStorageProperties.getPublicMedia().getBucket());
        entity.setObjectKey(objectKey);
        entity.setMimeType(mimeType.isBlank() ? "application/octet-stream" : mimeType);
        entity.setFileSizeBytes(bytes.length);
        entity.setChecksum(sha256(bytes));
        entity.setUploadedByUserId(currentUserService.currentUser().userId());
        FileEntity saved = fileRepository.save(entity);

        return new ShopImageUploadData(
                saved.getId(),
                rule.apiValue(),
                entity.getMimeType(),
                entity.getFileSizeBytes(),
                width,
                height,
                rule.ratioWidth(),
                rule.ratioHeight(),
                originalFilename
        );
    }

    private String buildObjectKey(String directory, String safeName) {
        LocalDate today = LocalDate.now();
        String random = UUID.randomUUID().toString().replace("-", "");
        return "%s/%d/%02d/%s_%s".formatted(directory, today.getYear(), today.getMonthValue(), random, safeName);
    }

    private Path resolveLocalRoot() {
        return Path.of(shopMediaStorageProperties.getLocalRoot()).normalize();
    }

    private boolean useS3() {
        return STORAGE_PROVIDER_S3.equalsIgnoreCase(shopMediaStorageProperties.getProvider());
    }

    private void storeBytes(String bucketName, String objectKey, byte[] bytes, String mimeType, String storageProvider) {
        if (STORAGE_PROVIDER_S3.equalsIgnoreCase(storageProvider)) {
            try {
                s3Client().putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectKey)
                                .contentType(mimeType)
                                .build(),
                        RequestBody.fromBytes(bytes)
                );
            } catch (RuntimeException exception) {
                throw new BusinessException("FILE_STORE_FAILED", "Unable to upload image file to S3.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return;
        }
        Path destination = resolveLocalRoot().resolve(bucketName).resolve(objectKey);
        try {
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
        } catch (IOException exception) {
            throw new BusinessException("FILE_STORE_FAILED", "Unable to store image file.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private S3Client s3Client() {
        S3Client existing = s3Client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (s3Client == null) {
                s3Client = S3Client.builder()
                        .region(Region.of(shopMediaStorageProperties.getAwsRegion()))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            }
            return s3Client;
        }
    }

    private String sanitizeFilename(String filename) {
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace("\\", "_").replace("/", "_");
        normalized = normalized.replaceAll("[^a-z0-9._-]", "_");
        if (normalized.isBlank()) {
            return "file";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private enum ImageAssetRule {
        PRODUCT_ITEM("PRODUCT_ITEM", 4, 5, 1200, 1500, 5 * 1024 * 1024L),
        SHOP_LOGO("SHOP_LOGO", 1, 1, 800, 800, 2 * 1024 * 1024L),
        SHOP_COVER("SHOP_COVER", 16, 9, 1600, 900, 8 * 1024 * 1024L);

        private final String apiValue;
        private final int ratioWidth;
        private final int ratioHeight;
        private final int minWidth;
        private final int minHeight;
        private final long maxBytes;

        ImageAssetRule(String apiValue, int ratioWidth, int ratioHeight, int minWidth, int minHeight, long maxBytes) {
            this.apiValue = apiValue;
            this.ratioWidth = ratioWidth;
            this.ratioHeight = ratioHeight;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.maxBytes = maxBytes;
        }

        static ImageAssetRule from(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            for (ImageAssetRule rule : values()) {
                if (rule.apiValue.equals(normalized)) {
                    return rule;
                }
            }
            throw new BusinessException("INVALID_IMAGE_ASSET_TYPE", "Unsupported image section type.", HttpStatus.BAD_REQUEST);
        }

        boolean isMimeTypeAllowed(String mimeType) {
            return "image/jpeg".equals(mimeType) || "image/jpg".equals(mimeType) || "image/png".equals(mimeType);
        }

        void validateDimensions(int width, int height) {
            if (width < minWidth || height < minHeight) {
                throw new BusinessException(
                        "IMAGE_TOO_SMALL",
                        "Image is too small. Minimum required size is %d x %d.".formatted(minWidth, minHeight),
                        HttpStatus.BAD_REQUEST
                );
            }
            double expectedRatio = (double) ratioWidth / ratioHeight;
            double actualRatio = (double) width / height;
            double delta = Math.abs(actualRatio - expectedRatio) / expectedRatio;
            if (delta > 0.03d) {
                throw new BusinessException(
                        "INVALID_IMAGE_RATIO",
                        "Wrong image ratio. Required ratio is %d:%d.".formatted(ratioWidth, ratioHeight),
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        public String apiValue() {
            return apiValue;
        }

        public String directory() {
            return apiValue.toLowerCase(Locale.ROOT);
        }

        public String objectKeyPrefix(ShopMediaStorageProperties properties) {
            return switch (this) {
                case PRODUCT_ITEM -> properties.getPrefixes().getShopProducts();
                case SHOP_LOGO -> properties.getPrefixes().getShopLogos();
                case SHOP_COVER -> properties.getPrefixes().getShopCovers();
            };
        }

        public int ratioWidth() {
            return ratioWidth;
        }

        public int ratioHeight() {
            return ratioHeight;
        }

        public long maxBytes() {
            return maxBytes;
        }
    }
}
