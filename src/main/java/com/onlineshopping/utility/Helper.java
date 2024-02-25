package com.onlineshopping.utility;

import java.util.Random;

public class Helper {

	public static String getAlphaNumericOrderId() {

		String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "abcdefghijklmnopqrstuvxyz";

		StringBuilder sb = new StringBuilder(10);

		for (int i = 0; i < 10; i++) {

			int index = (int) (AlphaNumericString.length() * Math.random());

			sb.append(AlphaNumericString.charAt(index));
		}

		return sb.toString().toUpperCase();
	}

	public static String generateOTP() {

		// Define the characters that can be used in the OTP
		String digits = "0123456789";

		// Initialize a random number generator
		Random random = new Random();

		// Create a StringBuilder to build the OTP
		StringBuilder otpBuilder = new StringBuilder(6);

		// Generate random digits for the OTP
		for (int i = 0; i < 6; i++) {
			int index = random.nextInt(digits.length());
			char digit = digits.charAt(index);
			otpBuilder.append(digit);
		}

		return otpBuilder.toString();
	}

}
