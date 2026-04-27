package com.msa.shop_orders.provider.shop.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class ShopMediaStorageProperties {
    private String provider = "LOCAL";
    private String localRoot = "../Auth-VerificationService/storage/local";
    private String awsRegion = "us-east-1";
    private final BucketProperties publicMedia = new BucketProperties();
    private final PrefixProperties prefixes = new PrefixProperties();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public BucketProperties getPublicMedia() {
        return publicMedia;
    }

    public PrefixProperties getPrefixes() {
        return prefixes;
    }

    public static class BucketProperties {
        private String bucket = "";

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class PrefixProperties {
        private String shopProducts = "shops/products";
        private String shopLogos = "shops/logo";
        private String shopCovers = "shops/cover";

        public String getShopProducts() {
            return shopProducts;
        }

        public void setShopProducts(String shopProducts) {
            this.shopProducts = shopProducts;
        }

        public String getShopLogos() {
            return shopLogos;
        }

        public void setShopLogos(String shopLogos) {
            this.shopLogos = shopLogos;
        }

        public String getShopCovers() {
            return shopCovers;
        }

        public void setShopCovers(String shopCovers) {
            this.shopCovers = shopCovers;
        }
    }
}
