package com.msa.shop_orders.common.mongo;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class MongoSequenceService {
    private final MongoOperations mongoOperations;

    public MongoSequenceService(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public long nextValue(String key) {
        Query query = new Query(Criteria.where("_id").is(key));
        Update update = new Update().inc("seq", 1L);
        DatabaseSequence counter = mongoOperations.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DatabaseSequence.class
        );
        return counter == null ? 1L : counter.getSeq();
    }
}
