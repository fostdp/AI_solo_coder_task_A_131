package com.nestcart.repository;

import com.nestcart.entity.NestCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NestCartRepository extends JpaRepository<NestCart, UUID> {
}
