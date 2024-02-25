package com.onlineshopping.resource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.onlineshopping.dao.AddressDao;
import com.onlineshopping.dao.OtpVerificationDao;
import com.onlineshopping.dao.UserDao;
import com.onlineshopping.dto.AddUserRequest;
import com.onlineshopping.dto.UserLoginRequest;
import com.onlineshopping.dto.UserResponse;
import com.onlineshopping.dto.UserVerifyRegisterRequest;
import com.onlineshopping.model.Address;
import com.onlineshopping.model.OtpVerification;
import com.onlineshopping.model.User;
import com.onlineshopping.service.EmailService;
import com.onlineshopping.utility.Helper;

@Component
@Transactional
public class UserResource {

	@Autowired
	private UserDao userDao;

	@Autowired
	private AddressDao addressDao;

	@Autowired
	private EmailService emailService;

	@Autowired
	private OtpVerificationDao otpVerificationDao;

	public ResponseEntity<UserResponse> registerUser(AddUserRequest userRequest) {
		UserResponse response = new UserResponse();

		if (userRequest == null) {
			response.setResponseMessage("bad request - missing request");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (!AddUserRequest.validate(userRequest)) {
			response.setResponseMessage("bad request - missing input");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (userRequest.getPhoneNo().length() != 10) {
			response.setResponseMessage("Enter Valid Mobile No");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		Address address = new Address();
		address.setCity(userRequest.getCity());
		address.setPincode(userRequest.getPincode());
		address.setStreet(userRequest.getStreet());

		Address addAddress = addressDao.save(address);

		if (addAddress == null) {
			response.setResponseMessage("Failed to register User");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		User user = new User();
		user.setAddress(addAddress);
		user.setEmailId(userRequest.getEmailId());
		user.setFirstName(userRequest.getFirstName());
		user.setLastName(userRequest.getLastName());
		user.setPhoneNo(userRequest.getPhoneNo());
		user.setPassword(userRequest.getPassword());
		user.setRole(userRequest.getRole());
//		User addUser = userDao.save(user);

		String otp = Helper.generateOTP();
		System.out.println("SENT OTP: " + otp);

		OtpVerification otpVerification = new OtpVerification();
		otpVerification.setEmailId(userRequest.getEmailId());
		otpVerification.setOtp(otp);
		otpVerification.setRole(userRequest.getRole());

		otpVerificationDao.save(otpVerification);

		String toEmail = userRequest.getEmailId();
		String subject = "E-commerce Online Shopping - Verify Your Email Address for User Registration";
		String message = "User Registration OTP for Ecommerce Website: " + otp + ". Please keep it confidential.";

		System.out.println(subject);
		System.out.println(message);

		try {
			this.emailService.sendEmail(toEmail, subject, message);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		response.setUsers(Arrays.asList(user));
		response.setResponseMessage("An OTP has been sent to your email. Please verify.");

		response.setSuccess(true);

		return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserResponse> loginUser(UserLoginRequest loginRequest) {
		UserResponse response = new UserResponse();

		if (loginRequest == null) {
			response.setResponseMessage("bad request - missing request");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (!UserLoginRequest.validateLoginRequest(loginRequest)) {
			response.setResponseMessage("bad request - missing input");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User user = new User();
		user = userDao.findByEmailIdAndPasswordAndRole(loginRequest.getEmailId(), loginRequest.getPassword(),
				loginRequest.getRole());

		if (user == null) {
			response.setResponseMessage("Invalid Log In Credentials!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		response.setUsers(Arrays.asList(user));
		response.setResponseMessage("Logged in Successful");
		response.setSuccess(true);

		return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserResponse> getAllDeliveryPersons() {
		UserResponse response = new UserResponse();

		List<User> deliveryPersons = this.userDao.findByRole("Delivery");

		if (CollectionUtils.isEmpty(deliveryPersons)) {
			response.setResponseMessage("No Delivery Person Found");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}

		response.setUsers(deliveryPersons);
		response.setResponseMessage("Delivery Persons Fected Success!!!");
		response.setSuccess(true);

		return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserResponse> forgetPassword(UserLoginRequest request) {
		UserResponse response = new UserResponse();

		if (request == null) {
			response.setResponseMessage("bad request - missing request");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (!UserLoginRequest.validateForgetRequest(request)) {
			response.setResponseMessage("bad request - missing input");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User existingCustomer = this.userDao.findByEmailIdAndRole(request.getEmailId(), "Customer");

		if (existingCustomer == null) {
			response.setResponseMessage("User with this email id, Not Exist!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
		}

//		User user = new User();
//		user = userDao.findByEmailIdAndPasswordAndRole(request.getEmailId(), request.getPassword(), "Customer");
//
//		if (user == null) {
//			response.setResponseMessage("Current Password is Wrong!!!");
//			response.setSuccess(false);
//
//			return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
//		}

//		user.setPassword(request.getNewPassword());
//
//		User addUser = userDao.save(user);
//
//		if (addUser == null) {
//			throw new UserSaveFailedException("Failed to update the Password");
//		}
		
		String otp = Helper.generateOTP();
		System.out.println("SENT OTP: " + otp);

		OtpVerification otpVerification = new OtpVerification();
		otpVerification.setEmailId(request.getEmailId());
		otpVerification.setOtp(otp);
		otpVerification.setRole(existingCustomer.getRole());

		otpVerificationDao.save(otpVerification);

		String toEmail = request.getEmailId();
		String subject = "E-commerce Online Shopping - Verify OTP for Forget Password";
		String message = "Forget Password Verificatoion OTP for Ecommerce Website: " + otp + ". Please keep it confidential.";

		System.out.println(subject);
		System.out.println(message);

		try {
			this.emailService.sendEmail(toEmail, subject, message);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		User forgetPassUser = new User();
		forgetPassUser.setId(existingCustomer.getId());
		forgetPassUser.setPassword(request.getNewPassword());
		forgetPassUser.setEmailId(request.getEmailId());
		forgetPassUser.setRole(existingCustomer.getRole());
		
		response.setUsers(Arrays.asList(forgetPassUser));

		response.setResponseMessage("An OTP has been sent to your email. Please verify.");
		response.setSuccess(true);

		return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<?> verifyAndRegister(UserVerifyRegisterRequest request) {
		UserResponse response = new UserResponse();

		List<OtpVerification> otpVerifications = this.otpVerificationDao
				.findByEmailIdAndRoleOrderByIdDesc(request.getUser().getEmailId(), request.getUser().getRole());

		if (CollectionUtils.isEmpty(otpVerifications)) {
			response.setResponseMessage("Failed to Verify the OTP!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}

		OtpVerification otpVerification = otpVerifications.get(0);

		if (otpVerification.getOtp().equals(request.getOtp())) {
			User registeredUser = this.userDao.save(request.getUser());

			this.otpVerificationDao.delete(otpVerification);

			response.setResponseMessage("User Registered Successful!!!");
			response.setSuccess(true);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}

		else {
			this.otpVerificationDao.delete(otpVerification);
			response.setResponseMessage("OTP Verification Failed!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}
	}

	public ResponseEntity<?> verifyAndChangePassword(UserVerifyRegisterRequest request) {
		UserResponse response = new UserResponse();

		List<OtpVerification> otpVerifications = this.otpVerificationDao
				.findByEmailIdAndRoleOrderByIdDesc(request.getUser().getEmailId(), request.getUser().getRole());

		if (CollectionUtils.isEmpty(otpVerifications)) {
			response.setResponseMessage("Failed to Verify the OTP!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}

		OtpVerification otpVerification = otpVerifications.get(0);

		if (otpVerification.getOtp().equals(request.getOtp())) {
			
			Optional<User> optional = this.userDao.findById(request.getUser().getId());
			
			User user = null;
			
			if(optional.isPresent()) {
				user = optional.get();
			}
		
			if(user == null) {
				response.setResponseMessage("OTP Verification Failed!!!");
				response.setSuccess(false);

				return new ResponseEntity<UserResponse>(response, HttpStatus.BAD_REQUEST);
			}
			
			user.setPassword(request.getUser().getPassword());
			
			this.userDao.save(user);

			this.otpVerificationDao.delete(otpVerification);

			response.setResponseMessage("Password Changed Successful!!!");
			response.setSuccess(true);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}

		else {
			this.otpVerificationDao.delete(otpVerification);
			response.setResponseMessage("OTP Verification Failed!!!");
			response.setSuccess(false);

			return new ResponseEntity<UserResponse>(response, HttpStatus.OK);
		}
	}

}
