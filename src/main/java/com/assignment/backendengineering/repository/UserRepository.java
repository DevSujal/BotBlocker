package com.assignment.backendengineering.repository;

import com.assignment.backendengineering.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}
