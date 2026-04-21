package com.msa.shop_orders.persistence.mongo.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "mongo_sequences")
public class MongoSequenceDocument {
    @Id
    private String id;
    private long value;
}
