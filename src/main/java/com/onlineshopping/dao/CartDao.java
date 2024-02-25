package com.onlineshopping.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.onlineshopping.model.Cart;

@Repository
public interface CartDao extends JpaRepository<Cart, Integer> {	
	
	List<Cart> findByUser_id(int userId);

}
