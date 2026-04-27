package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, Long> {
    List<UserAddressEntity> findByUserIdAndAddressScopeOrderByDefaultAddressDescIdAsc(Long userId, String addressScope);
}
