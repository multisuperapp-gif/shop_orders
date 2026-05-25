package com.msa.shop_orders.common.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sequence_counters")
@Getter
@Setter
public class DatabaseSequence {
    @Id
    private String id;
    private long seq;
}
