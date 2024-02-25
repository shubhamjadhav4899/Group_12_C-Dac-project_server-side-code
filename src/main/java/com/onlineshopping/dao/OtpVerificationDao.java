package com.onlineshopping.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.onlineshopping.model.OtpVerification;

@Repository
public interface OtpVerificationDao extends JpaRepository<OtpVerification, Integer> {

	List<OtpVerification> findByEmailIdAndRoleOrderByIdDesc(String emailId, String role);

}
