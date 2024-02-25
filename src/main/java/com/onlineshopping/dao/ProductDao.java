package com.onlineshopping.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.onlineshopping.model.Product;

@Repository
public interface ProductDao extends JpaRepository<Product, Integer> {
	
	List<Product> findByCategoryId(int category);
	
	@Modifying
    @Query("DELETE FROM Cart c WHERE c.product = :product")
    void deleteCartsByProduct(@Param("product") Product product);



}
