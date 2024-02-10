package com.cdacproject.medezee.service;

import java.util.List;

import com.cdacproject.medezee.model.User;

public interface IUserService {
    User registerUser(User user);
    List<User> getUsers();
    void deleteUser(String email);
    User getUser(String email);
}
