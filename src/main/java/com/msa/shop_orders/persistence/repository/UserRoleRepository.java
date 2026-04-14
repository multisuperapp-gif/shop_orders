package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.UserEntity;
import com.msa.shop_orders.persistence.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {
    List<UserRoleEntity> findByUser_IdAndActiveTrue(Long userId);

    Optional<UserRoleEntity> findByUserAndRole_Name(UserEntity user, String roleName);
}
