package com.onlineshopping.resource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.transaction.Transactional;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.dao.CartDao;
import com.onlineshopping.dao.OrderDao;
import com.onlineshopping.dao.PgTransactionDao;
import com.onlineshopping.dao.ProductDao;
import com.onlineshopping.dao.UserDao;
import com.onlineshopping.dto.CommonApiResponse;
import com.onlineshopping.dto.MyOrderResponse;
import com.onlineshopping.dto.OrderRazorPayResponse;
import com.onlineshopping.dto.UpdateDeliveryStatusRequest;
import com.onlineshopping.dto.UserOrderResponse;
import com.onlineshopping.exception.OrderSaveFailedException;
import com.onlineshopping.model.Cart;
import com.onlineshopping.model.Orders;
import com.onlineshopping.model.PgTransaction;
import com.onlineshopping.model.Product;
import com.onlineshopping.model.User;
import com.onlineshopping.pg.Notes;
import com.onlineshopping.pg.Prefill;
import com.onlineshopping.pg.RazorPayPaymentRequest;
import com.onlineshopping.pg.RazorPayPaymentResponse;
import com.onlineshopping.pg.Theme;
import com.onlineshopping.service.EmailService;
import com.onlineshopping.utility.Constants.DeliveryStatus;
import com.onlineshopping.utility.Constants.DeliveryTime;
import com.onlineshopping.utility.Constants.IsDeliveryAssigned;
import com.onlineshopping.utility.Constants.PaymentGatewayTxnStatus;
import com.onlineshopping.utility.Constants.PaymentGatewayTxnType;
import com.onlineshopping.utility.Helper;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@Component
@Transactional
public class OrderResource {

	private final Logger LOG = LoggerFactory.getLogger(OrderResource.class);

	@Autowired
	private OrderDao orderDao;

	@Autowired
	private CartDao cartDao;

	@Autowired
	private UserDao userDao;

	@Autowired
	private ProductDao productDao;

	@Autowired
	private EmailService emailService;

	@Autowired
	private PgTransactionDao pgTransactionDao;

	@Autowired
	private ObjectMapper objectMapper;

	public ResponseEntity<CommonApiResponse> customerOrder(int userId, String razorPayOrderId) {
		CommonApiResponse response = new CommonApiResponse();

		if (userId == 0) {
			response.setResponseMessage("bad request - missing field");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<Cart> userCarts = cartDao.findByUser_id(userId);

		if (CollectionUtils.isEmpty(userCarts)) {
			response.setResponseMessage("Your Cart is Empty!!!");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		LocalDateTime currentDateTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
		String formatDateTime = currentDateTime.format(formatter);

		Optional<User> optional = this.userDao.findById(userId);

		User customer = null;

		if (optional.isPresent()) {
			customer = optional.get();
		}

		if (customer == null) {
			response.setResponseMessage("Customer Not found!!!");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<Product> customerOrderedProducts = new ArrayList<>();

		try {

			for (Cart cart : userCarts) {

				Orders order = new Orders();
				order.setOrderId(razorPayOrderId);
				order.setUser(cart.getUser());
				order.setProduct(cart.getProduct());
				order.setQuantity(cart.getQuantity());
				order.setOrderDate(formatDateTime);
				order.setDeliveryDate(DeliveryStatus.PENDING.value());
				order.setDeliveryStatus(DeliveryStatus.PENDING.value());
				order.setDeliveryTime(DeliveryTime.DEFAULT.value());
				order.setDeliveryAssigned(IsDeliveryAssigned.NO.value());

				customerOrderedProducts.add(cart.getProduct());

				Orders savedOrder = orderDao.save(order);

				if (savedOrder == null) {
					throw new OrderSaveFailedException("Failed to save the Order");
				}
				cartDao.delete(cart);
			}

			String mailBody = sendOrderConfirmationMail(customer, userCarts, razorPayOrderId);
			String subject = "E-commerce Online Shopping - Order Confirmation";

			this.emailService.sendEmail(customer.getEmailId(), subject, mailBody);

		} catch (Exception e) {
			response.setResponseMessage("Failed to Order Products!!!");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		response.setResponseMessage("Your Order Placed, Order Id: " + razorPayOrderId);
		response.setSuccess(true);

		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);
	}

	private String sendOrderConfirmationMail(User customer, List<Cart> carts, String orderId) {

		StringBuilder emailBody = new StringBuilder();
		emailBody.append("<html><body>");
		emailBody.append("<h3>Dear " + customer.getFirstName() + ",</h3>");
		emailBody.append("<p>Thank you for placing an order with us. Your Order Id is:<span><b>" + orderId + "</b><span></p>");

		emailBody.append("<h3>Ordered Products:</h3>");

		// Create a dynamic table for the list of orders
		emailBody.append("<table border='1'>");
		emailBody.append("<tr><th>Product</th><th>Quantity</th><th>Price</th></tr>");

		BigDecimal totalPrice = BigDecimal.ZERO;

		for (Cart cart : carts) {
			emailBody.append("<tr>");
			emailBody.append("<td>").append(cart.getProduct().getTitle()).append("</td>");
			emailBody.append("<td>").append(cart.getQuantity()).append("</td>");
			emailBody.append("<td>")
					.append(cart.getProduct().getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())))
					.append("</td>");
			emailBody.append("</tr>");

			totalPrice = totalPrice.add(cart.getProduct().getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
		}

		emailBody.append("</table>");

		emailBody.append("<h3>Total Price: &#8377;" + totalPrice + "/- </h3>");

		emailBody.append("<p>Best Regards,<br/>Ecommerce Team</p>");

		emailBody.append("</body></html>");

		return emailBody.toString();
	}

	public ResponseEntity<UserOrderResponse> getMyOrder(int userId) {
		UserOrderResponse response = new UserOrderResponse();

		if (userId == 0) {
			response.setResponseMessage("User Id missing");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<Orders> userOrder = orderDao.findByUser_id(userId);

		List<MyOrderResponse> orderDatas = new ArrayList<>();

		if (CollectionUtils.isEmpty(userOrder)) {
			response.setResponseMessage("Orders not found");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
		}

		for (Orders order : userOrder) {
			MyOrderResponse orderData = new MyOrderResponse();
			orderData.setOrderId(order.getOrderId());
			orderData.setProductDescription(order.getProduct().getDescription());
			orderData.setProductName(order.getProduct().getTitle());
			orderData.setProductImage(order.getProduct().getImageName());
			orderData.setQuantity(order.getQuantity());
			orderData.setOrderDate(order.getOrderDate());
			orderData.setProductId(order.getProduct().getId());
			orderData.setDeliveryDate(order.getDeliveryDate() + " " + order.getDeliveryTime());
			orderData.setDeliveryStatus(order.getDeliveryStatus());
			orderData.setTotalPrice(
					String.valueOf(order.getQuantity() * Double.parseDouble(order.getProduct().getPrice().toString())));
			if (order.getDeliveryPersonId() == 0) {
				orderData.setDeliveryPersonContact(DeliveryStatus.PENDING.value());
				orderData.setDeliveryPersonName(DeliveryStatus.PENDING.value());
			}

			else {

				User deliveryPerson = null;

				Optional<User> optionalDeliveryPerson = this.userDao.findById(order.getDeliveryPersonId());

				deliveryPerson = optionalDeliveryPerson.get();

				orderData.setDeliveryPersonContact(deliveryPerson.getPhoneNo());
				orderData.setDeliveryPersonName(deliveryPerson.getFirstName());
			}
			orderDatas.add(orderData);
		}

		response.setOrders(orderDatas);
		response.setResponseMessage("Order Fetched Successful!!");
		response.setSuccess(true);

		return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserOrderResponse> getAllOrder() {
		UserOrderResponse response = new UserOrderResponse();

		List<Orders> userOrder = orderDao.findAll();

		if (CollectionUtils.isEmpty(userOrder)) {
			response.setResponseMessage("Orders not found");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
		}

		List<MyOrderResponse> orderDatas = new ArrayList<>();

		for (Orders order : userOrder) {
			MyOrderResponse orderData = new MyOrderResponse();
			orderData.setOrderId(order.getOrderId());
			orderData.setProductDescription(order.getProduct().getDescription());
			orderData.setProductName(order.getProduct().getTitle());
			orderData.setProductImage(order.getProduct().getImageName());
			orderData.setQuantity(order.getQuantity());
			orderData.setOrderDate(order.getOrderDate());
			orderData.setProductId(order.getProduct().getId());
			orderData.setDeliveryDate(order.getDeliveryDate() + " " + order.getDeliveryTime());
			orderData.setDeliveryStatus(order.getDeliveryStatus());
			orderData.setTotalPrice(
					String.valueOf(order.getQuantity() * Double.parseDouble(order.getProduct().getPrice().toString())));
			orderData.setUserId(order.getUser().getId());
			orderData.setUserName(order.getUser().getFirstName() + " " + order.getUser().getLastName());
			orderData.setUserPhone(order.getUser().getPhoneNo());
			orderData.setAddress(order.getUser().getAddress());
			if (order.getDeliveryPersonId() == 0) {
				orderData.setDeliveryPersonContact(DeliveryStatus.PENDING.value());
				orderData.setDeliveryPersonName(DeliveryStatus.PENDING.value());
			}

			else {
				User deliveryPerson = null;

				Optional<User> optionalDeliveryPerson = this.userDao.findById(order.getDeliveryPersonId());

				deliveryPerson = optionalDeliveryPerson.get();

				orderData.setDeliveryPersonContact(deliveryPerson.getPhoneNo());
				orderData.setDeliveryPersonName(deliveryPerson.getFirstName());
			}
			orderDatas.add(orderData);

		}

		response.setOrders(orderDatas);
		response.setResponseMessage("Order Fetched Successful!!");
		response.setSuccess(true);

		return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserOrderResponse> getOrdersByOrderId(String orderId) {
		UserOrderResponse response = new UserOrderResponse();

		if (orderId == null) {
			response.setResponseMessage("Orders not found");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
		}

		List<Orders> userOrder = orderDao.findByOrderId(orderId);

		if (CollectionUtils.isEmpty(userOrder)) {
			response.setResponseMessage("Orders not found");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
		}

		List<MyOrderResponse> orderDatas = new ArrayList<>();

		for (Orders order : userOrder) {
			MyOrderResponse orderData = new MyOrderResponse();
			orderData.setOrderId(order.getOrderId());
			orderData.setProductDescription(order.getProduct().getDescription());
			orderData.setProductName(order.getProduct().getTitle());
			orderData.setProductImage(order.getProduct().getImageName());
			orderData.setQuantity(order.getQuantity());
			orderData.setOrderDate(order.getOrderDate());
			orderData.setProductId(order.getProduct().getId());
			orderData.setDeliveryDate(order.getDeliveryDate() + " " + order.getDeliveryTime());
			orderData.setDeliveryStatus(order.getDeliveryStatus());
			orderData.setTotalPrice(
					String.valueOf(order.getQuantity() * Double.parseDouble(order.getProduct().getPrice().toString())));
			orderData.setUserId(order.getUser().getId());
			orderData.setUserName(order.getUser().getFirstName() + " " + order.getUser().getLastName());
			orderData.setUserPhone(order.getUser().getPhoneNo());
			orderData.setAddress(order.getUser().getAddress());
			if (order.getDeliveryPersonId() == 0) {
				orderData.setDeliveryPersonContact(DeliveryStatus.PENDING.value());
				orderData.setDeliveryPersonName(DeliveryStatus.PENDING.value());
			}

			else {
				User deliveryPerson = null;

				Optional<User> optionalDeliveryPerson = this.userDao.findById(order.getDeliveryPersonId());

				deliveryPerson = optionalDeliveryPerson.get();

				orderData.setDeliveryPersonContact(deliveryPerson.getPhoneNo());
				orderData.setDeliveryPersonName(deliveryPerson.getFirstName());
			}
			orderDatas.add(orderData);

		}

		response.setOrders(orderDatas);
		response.setResponseMessage("Order Fetched Successful!!");
		response.setSuccess(true);

		return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserOrderResponse> updateOrderDeliveryStatus(UpdateDeliveryStatusRequest deliveryRequest) {
		UserOrderResponse response = new UserOrderResponse();

		if (deliveryRequest == null) {
			response.setResponseMessage("bad request - missing request");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<Orders> orders = orderDao.findByOrderId(deliveryRequest.getOrderId());

		if (CollectionUtils.isEmpty(orders)) {
			response.setResponseMessage("Orders not found!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		for (Orders order : orders) {
			order.setDeliveryDate(deliveryRequest.getDeliveryDate());
			order.setDeliveryStatus(deliveryRequest.getDeliveryStatus());
			order.setDeliveryTime(deliveryRequest.getDeliveryTime());
			orderDao.save(order);
		}

		List<Orders> userOrder = orderDao.findByOrderId(deliveryRequest.getOrderId());

		List<MyOrderResponse> orderDatas = new ArrayList<>();

		for (Orders order : userOrder) {
			MyOrderResponse orderData = new MyOrderResponse();
			orderData.setOrderId(order.getOrderId());
			orderData.setProductDescription(order.getProduct().getDescription());
			orderData.setProductName(order.getProduct().getTitle());
			orderData.setProductImage(order.getProduct().getImageName());
			orderData.setQuantity(order.getQuantity());
			orderData.setOrderDate(order.getOrderDate());
			orderData.setProductId(order.getProduct().getId());
			orderData.setDeliveryDate(order.getDeliveryDate() + " " + order.getDeliveryTime());
			orderData.setDeliveryStatus(order.getDeliveryStatus());
			orderData.setTotalPrice(
					String.valueOf(order.getQuantity() * Double.parseDouble(order.getProduct().getPrice().toString())));
			orderData.setUserId(order.getUser().getId());
			orderData.setUserName(order.getUser().getFirstName() + " " + order.getUser().getLastName());
			orderData.setUserPhone(order.getUser().getPhoneNo());
			orderData.setAddress(order.getUser().getAddress());
			if (order.getDeliveryPersonId() == 0) {
				orderData.setDeliveryPersonContact(DeliveryStatus.PENDING.value());
				orderData.setDeliveryPersonName(DeliveryStatus.PENDING.value());
			}

			else {
				User deliveryPerson = null;

				Optional<User> optionalDeliveryPerson = this.userDao.findById(order.getDeliveryPersonId());

				deliveryPerson = optionalDeliveryPerson.get();

				orderData.setDeliveryPersonContact(deliveryPerson.getPhoneNo());
				orderData.setDeliveryPersonName(deliveryPerson.getFirstName());
			}
			orderDatas.add(orderData);

		}

		response.setOrders(orderDatas);
		response.setResponseMessage("Order Fetched Successful!!");
		response.setSuccess(true);

		return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserOrderResponse> assignDeliveryPersonForOrder(UpdateDeliveryStatusRequest deliveryRequest) {
		UserOrderResponse response = new UserOrderResponse();

		if (deliveryRequest == null) {
			response.setResponseMessage("bad request - missing request");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<Orders> orders = orderDao.findByOrderId(deliveryRequest.getOrderId());

		if (CollectionUtils.isEmpty(orders)) {
			response.setResponseMessage("Orders not found!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		for (Orders order : orders) {
			order.setDeliveryAssigned(IsDeliveryAssigned.YES.value());
			order.setDeliveryPersonId(deliveryRequest.getDeliveryId());
			orderDao.save(order);
		}

		List<Orders> userOrder = orderDao.findByOrderId(deliveryRequest.getOrderId());

		List<MyOrderResponse> orderDatas = new ArrayList<>();

		for (Orders order : userOrder) {
			MyOrderResponse orderData = new MyOrderResponse();
			orderData.setOrderId(order.getOrderId());
			orderData.setProductDescription(order.getProduct().getDescription());
			orderData.setProductName(order.getProduct().getTitle());
			orderData.setProductImage(order.getProduct().getImageName());
			orderData.setQuantity(order.getQuantity());
			orderData.setOrderDate(order.getOrderDate());
			orderData.setProductId(order.getProduct().getId());
			orderData.setDeliveryDate(order.getDeliveryDate() + " " + order.getDeliveryTime());
			orderData.setDeliveryStatus(order.getDeliveryStatus());
			orderData.setTotalPrice(
					String.valueOf(order.getQuantity() * Double.parseDouble(order.getProduct().getPrice().toString())));
			orderData.setUserId(order.getUser().getId());
			orderData.setUserName(order.getUser().getFirstName() + " " + order.getUser().getLastName());
			orderData.setUserPhone(order.getUser().getPhoneNo());
			orderData.setAddress(order.getUser().getAddress());

			if (order.getDeliveryPersonId() == 0) {
				orderData.setDeliveryPersonContact(DeliveryStatus.PENDING.value());
				orderData.setDeliveryPersonName(DeliveryStatus.PENDING.value());
			}

			else {
				User dPerson = null;

				Optional<User> optionalPerson = this.userDao.findById(order.getDeliveryPersonId());

				dPerson = optionalPerson.get();

				orderData.setDeliveryPersonContact(dPerson.getPhoneNo());
				orderData.setDeliveryPersonName(dPerson.getFirstName());
			}

			orderDatas.add(orderData);

		}

		response.setOrders(orderDatas);
		response.setResponseMessage("Order Fetched Successful!!");
		response.setSuccess(true);

		return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserOrderResponse> getMyDeliveryOrders(int deliveryPersonId) {
		UserOrderResponse response = new UserOrderResponse();

		if (deliveryPersonId == 0) {
			response.setResponseMessage("bad request - missing field");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User person = null;

		Optional<User> oD = this.userDao.findById(deliveryPersonId);

		if (oD.isPresent()) {
			person = oD.get();
		}

		List<Orders> userOrder = orderDao.findByDeliveryPersonId(deliveryPersonId);

		if (CollectionUtils.isEmpty(userOrder)) {
			response.setResponseMessage("Orders not found!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserOrderResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<MyOrderResponse> orderDatas = new ArrayList<>();

		for (Orders order : userOrder) {
			MyOrderResponse orderData = new MyOrderResponse();
			orderData.setOrderId(order.getOrderId());
			orderData.setProductDescription(order.getProduct().getDescription());
			orderData.setProductName(order.getProduct().getTitle());
			orderData.setProductImage(order.getProduct().getImageName());
			orderData.setQuantity(order.getQuantity());
			orderData.setOrderDate(order.getOrderDate());
			orderData.setProductId(order.getProduct().getId());
			orderData.setDeliveryDate(order.getDeliveryDate() + " " + order.getDeliveryTime());
			orderData.setDeliveryStatus(order.getDeliveryStatus());
			orderData.setTotalPrice(
					String.valueOf(order.getQuantity() * Double.parseDouble(order.getProduct().getPrice().toString())));
			orderData.setUserId(order.getUser().getId());
			orderData.setUserName(order.getUser().getFirstName() + " " + order.getUser().getLastName());
			orderData.setUserPhone(order.getUser().getPhoneNo());
			orderData.setAddress(order.getUser().getAddress());

			if (order.getDeliveryPersonId() == 0) {
				orderData.setDeliveryPersonContact(DeliveryStatus.PENDING.value());
				orderData.setDeliveryPersonName(DeliveryStatus.PENDING.value());
			}

			else {
				orderData.setDeliveryPersonContact(person.getPhoneNo());
				orderData.setDeliveryPersonName(person.getFirstName());
			}

			orderDatas.add(orderData);

		}

		response.setOrders(orderDatas);
		response.setResponseMessage("Order Fetched Successful!!");
		response.setSuccess(true);

		return new ResponseEntity<UserOrderResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<OrderRazorPayResponse> createRazorPayOrder(int userId) throws RazorpayException {
		OrderRazorPayResponse response = new OrderRazorPayResponse();

		if (userId == 0) {
			response.setResponseMessage("bad request - user id is missing");
			response.setSuccess(false);

			return new ResponseEntity<OrderRazorPayResponse>(response, HttpStatus.BAD_REQUEST);
		}

		Optional<User> optional = this.userDao.findById(userId);

		User customer = null;

		if (optional.isPresent()) {
			customer = optional.get();
		}

		if (customer == null) {
			response.setResponseMessage("Customer Not found!!!");
			response.setSuccess(false);

			return new ResponseEntity<OrderRazorPayResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<Cart> userCarts = cartDao.findByUser_id(userId);

		if (CollectionUtils.isEmpty(userCarts)) {
			response.setResponseMessage("Your Cart is Empty!!!");
			response.setSuccess(false);

			return new ResponseEntity<OrderRazorPayResponse>(response, HttpStatus.BAD_REQUEST);
		}

		String requestTime = String
				.valueOf(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		BigDecimal totalPrice = BigDecimal.ZERO;

		for (Cart cart : userCarts) {
			totalPrice = totalPrice.add(cart.getProduct().getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
		}

		// write payment gateway code here

		// key : rzp_test_9C5DF9gbJINYTA
		// secret: WYqJeY6CJD1iw7cDZFv1eWl0

		String receiptId = generateUniqueRefId();

		RazorpayClient razorpay = new RazorpayClient("rzp_test_HHGQ8MLmONEzaL", "thlcg5dpDRvuh5s9SQxC57Cp");

		JSONObject orderRequest = new JSONObject();
		orderRequest.put("amount", convertRupeesToPaisa(totalPrice));
		orderRequest.put("currency", "INR");
		orderRequest.put("receipt", receiptId);
		JSONObject notes = new JSONObject();
		notes.put("note", "Product Order Payment - Ecommerce Online Shopping");
		orderRequest.put("notes", notes);

		Order order = razorpay.orders.create(orderRequest);

		if (order == null) {
			LOG.error("Null Response from RazorPay for creation of Order");
			response.setResponseMessage("Failed to Order the Products");
			response.setSuccess(false);
			return new ResponseEntity<OrderRazorPayResponse>(response, HttpStatus.BAD_REQUEST);
		}

		LOG.info(order.toString()); // printing the response which we got from RazorPay

		String orderId = order.get("id");

		PgTransaction createOrder = new PgTransaction();
		createOrder.setAmount(totalPrice);
		createOrder.setReceiptId(receiptId);
		createOrder.setRequestTime(requestTime);
		createOrder.setType(PaymentGatewayTxnType.CREATE_ORDER.value());
		createOrder.setUser(customer);
		createOrder.setOrderId(orderId); // fetching order id which is created at Razor Pay which we got in response

		if (order.get("status").equals("created")) {
			createOrder.setStatus(PaymentGatewayTxnStatus.SUCCESS.value());
		} else {
			createOrder.setStatus(PaymentGatewayTxnStatus.FAILED.value());
		}

		PgTransaction saveCreateOrderTxn = this.pgTransactionDao.save(createOrder);

		if (saveCreateOrderTxn == null) {
			LOG.error("Failed to save Payment Gateway CReate Order entry in DB");
		}

		PgTransaction payment = new PgTransaction();
		payment.setAmount(totalPrice);
		payment.setReceiptId(receiptId);
		payment.setRequestTime(requestTime);
		payment.setType(PaymentGatewayTxnType.PAYMENT.value());
		payment.setUser(customer);
		payment.setOrderId(orderId); // fetching order id which is created at Razor Pay which we got in response
		payment.setStatus(PaymentGatewayTxnStatus.FAILED.value());
		// from callback api we will actual response from RazorPay, initially keeping it
		// FAILED, once get success response from PG,
		// we will update it

		PgTransaction savePaymentTxn = this.pgTransactionDao.save(payment);

		if (savePaymentTxn == null) {
			LOG.error("Failed to save Payment Gateway Payment entry in DB");
		}

		// Creating RazorPayPaymentRequest to send to Frontend

		RazorPayPaymentRequest razorPayPaymentRequest = new RazorPayPaymentRequest();
		razorPayPaymentRequest.setAmount(convertRupeesToPaisa(totalPrice));
		// razorPayPaymentRequest.setCallbackUrl("http://localhost:8080/pg/razorPay/callBack/response");
		razorPayPaymentRequest.setCurrency("INR");
		razorPayPaymentRequest.setDescription("Product Order - Ecommerce Online Shopping");
		razorPayPaymentRequest.setImage(
				"https://banner2.cleanpng.com/20180926/qau/kisspng-computer-icons-scalable-vector-graphics-applicatio-tynor-wrist-splint-ambidextrous-rs-274-wrist-s-5bac3149dcb297.944285061538011465904.jpg");
		razorPayPaymentRequest.setKey("rzp_test_9C5DF9gbJINYTA");
		razorPayPaymentRequest.setName("MEDEZEE Online Medical Store");

		Notes note = new Notes();
		note.setAddress("Dummy Address");

		razorPayPaymentRequest.setNotes(note);
		razorPayPaymentRequest.setOrderId(orderId);

		Prefill prefill = new Prefill();
		prefill.setContact(customer.getPhoneNo());
		prefill.setEmail(customer.getEmailId());
		prefill.setName(customer.getFirstName() + " " + customer.getLastName());

		razorPayPaymentRequest.setPrefill(prefill);

		Theme theme = new Theme();
		theme.setColor("#25493F");

		razorPayPaymentRequest.setTheme(theme);

		try {
			String jsonRequest = objectMapper.writeValueAsString(razorPayPaymentRequest);
			System.out.println("*****************");
			System.out.println(jsonRequest);
			System.out.println("*****************");
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

//				customer.setWalletAmount(existingWalletAmount.add(request.getWalletAmount()));
		//
//				User updatedCustomer = this.userService.updateUser(customer);
		//
//				if (updatedCustomer == null) {
//					response.setResponseMessage("Failed to update the Wallet");
//					response.setSuccess(false);
//					return new ResponseEntity<UserWalletUpdateResponse>(response, HttpStatus.BAD_REQUEST);
//				}

		response.setRazorPayRequest(razorPayPaymentRequest);
		response.setResponseMessage("Payment Order Created Successful!!!");
		response.setSuccess(true);

		return new ResponseEntity<OrderRazorPayResponse>(response, HttpStatus.OK);

	}

	private int convertRupeesToPaisa(BigDecimal rupees) {
		// Multiply the rupees by 100 to get the equivalent in paisa
		BigDecimal paisa = rupees.multiply(new BigDecimal(100));
		return paisa.intValue();
	}

	// for razor pay receipt id
	private String generateUniqueRefId() {
		// Get current timestamp in milliseconds
		long currentTimeMillis = System.currentTimeMillis();

		// Generate a 6-digit UUID (random number)
		String randomDigits = UUID.randomUUID().toString().substring(0, 6);

		// Concatenate timestamp and random digits
		String uniqueRefId = currentTimeMillis + "-" + randomDigits;

		return uniqueRefId;
	}

	public ResponseEntity<CommonApiResponse> handleRazorPayPaymentResponse(RazorPayPaymentResponse razorPayResponse) {

		LOG.info("razor pay response came from frontend");

		CommonApiResponse response = new CommonApiResponse();

		if (razorPayResponse == null || razorPayResponse.getRazorpayOrderId() == null) {
			response.setResponseMessage("Invalid Input response");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		PgTransaction paymentTransaction = this.pgTransactionDao
				.findByTypeAndOrderId(PaymentGatewayTxnType.PAYMENT.value(), razorPayResponse.getRazorpayOrderId());

		if (paymentTransaction == null) {
			response.setResponseMessage("Failed to Order the Products");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}
		
		User customer = paymentTransaction.getUser();

		String razorPayRawResponse = "";
		try {
			razorPayRawResponse = objectMapper.writeValueAsString(razorPayResponse);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		paymentTransaction.setRawResponse(razorPayRawResponse);

		if (razorPayResponse.getError() == null) {
			paymentTransaction.setStatus(PaymentGatewayTxnStatus.SUCCESS.value());
		} else {
			paymentTransaction.setStatus(PaymentGatewayTxnStatus.FAILED.value());
		}

		PgTransaction updatedTransaction = this.pgTransactionDao.save(paymentTransaction);

		if (updatedTransaction.getStatus().equals(PaymentGatewayTxnStatus.FAILED.value())) {
			response.setResponseMessage("Failed to update the User Wallet");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);
		} else {
			return customerOrder(customer.getId(), razorPayResponse.getRazorpayOrderId());
		}

	}

}
