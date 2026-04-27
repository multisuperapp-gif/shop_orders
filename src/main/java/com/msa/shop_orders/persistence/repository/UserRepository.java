package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
}
