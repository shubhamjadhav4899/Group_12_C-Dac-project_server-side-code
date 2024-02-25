package com.onlineshopping.utility;

public class Constants {

	public enum DeliveryStatus {
		DELIVERED("Delivered"), ON_THE_WAY("On the Way"), PENDING("Pending"), // If admin doesn't take any action
		PROCESSING("Processing");

		private String status;

		private DeliveryStatus(String status) {
			this.status = status;
		}

		public String value() {
			return this.status;
		}

	}

	public enum DeliveryTime {
		MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening"), NIGHT("Night"), DEFAULT("");

		private String time;

		private DeliveryTime(String time) {
			this.time = time;
		}

		public String value() {
			return this.time;
		}

	}

	public enum IsDeliveryAssigned {
		YES("Yes"), NO("No");

		private String isDeliveryAssigned;

		private IsDeliveryAssigned(String isDeliveryAssigned) {
			this.isDeliveryAssigned = isDeliveryAssigned;
		}

		public String value() {
			return this.isDeliveryAssigned;
		}

	}

	public enum PaymentGatewayTxnType {
		CREATE_ORDER("Create Order"), PAYMENT("Payment");

		private String type;

		private PaymentGatewayTxnType(String type) {
			this.type = type;
		}

		public String value() {
			return this.type;
		}
	}

	public enum PaymentGatewayTxnStatus {
		SUCCESS("Success"), FAILED("Failed");

		private String type;

		private PaymentGatewayTxnStatus(String type) {
			this.type = type;
		}

		public String value() {
			return this.type;
		}
	}

}
