package com.cdacproject.medezee.service;

import java.util.List;

import com.cdacproject.medezee.model.Role;
import com.cdacproject.medezee.model.User;

public interface IRoleService {
    List<Role> getRoles();
    Role createRole(Role theRole);

    void deleteRole(Long id);
    Role findByName(String name);

    User removeUserFromRole(Long userId, Long roleId);
    User assignRoleToUser(Long userId, Long roleId);
    Role removeAllUsersFromRole(Long roleId);
}
