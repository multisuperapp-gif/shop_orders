package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_user_id", nullable = false, unique = true, length = 26)
    private String publicUserId;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_role_id")
    private RoleEntity primaryRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPublicUserId() {
        return publicUserId;
    }

    public void setPublicUserId(String publicUserId) {
        this.publicUserId = publicUserId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public RoleEntity getPrimaryRole() {
        return primaryRole;
    }

    public void setPrimaryRole(RoleEntity primaryRole) {
        this.primaryRole = primaryRole;
    }
}
