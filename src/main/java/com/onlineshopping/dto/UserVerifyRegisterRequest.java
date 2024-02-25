package com.onlineshopping.dto;

import com.onlineshopping.model.User;

public class UserVerifyRegisterRequest {

	private User user;

	private String otp;

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getOtp() {
		return otp;
	}

	public void setOtp(String otp) {
		this.otp = otp;
	}

}
