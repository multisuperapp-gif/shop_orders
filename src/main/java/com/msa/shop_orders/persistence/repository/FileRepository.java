package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
}
