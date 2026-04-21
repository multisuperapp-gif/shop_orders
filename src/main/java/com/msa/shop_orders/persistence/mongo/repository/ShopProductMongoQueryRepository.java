package com.msa.shop_orders.persistence.mongo.repository;

import com.msa.shop_orders.persistence.mongo.document.ShopProductDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class ShopProductMongoQueryRepository {
    private final MongoTemplate mongoTemplate;

    public ShopProductMongoQueryRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean existsBySkuIgnoreCase(String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        Query query = Query.query(Criteria.where("sku").regex("^" + Pattern.quote(sku.trim()) + "$", "i"));
        query.limit(1);
        return mongoTemplate.exists(query, ShopProductDocument.class);
    }

    public List<ShopProductDocument> searchActive(Long shopId, Long categoryId, String search) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("shopId").is(shopId));
        criteria.add(Criteria.where("active").is(true));
        if (categoryId != null) {
            criteria.add(Criteria.where("shopCategoryId").is(categoryId));
        }
        if (search != null && !search.isBlank()) {
            String pattern = Pattern.quote(search.trim());
            criteria.add(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("brandName").regex(pattern, "i")
            ));
        }
        Query query = Query.query(new Criteria().andOperator(criteria));
        query.with(Sort.by(Sort.Direction.DESC, "featured").and(Sort.by(Sort.Direction.DESC, "updatedAt")));
        return mongoTemplate.find(query, ShopProductDocument.class);
    }
}
