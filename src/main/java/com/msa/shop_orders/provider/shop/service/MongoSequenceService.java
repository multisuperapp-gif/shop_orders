package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.mongo.document.MongoSequenceDocument;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class MongoSequenceService {
    private final MongoTemplate mongoTemplate;

    public MongoSequenceService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long next(String name) {
        MongoSequenceDocument sequence = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(name)),
                new Update().inc("value", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                MongoSequenceDocument.class
        );
        return sequence == null ? 1L : sequence.getValue();
    }
}
