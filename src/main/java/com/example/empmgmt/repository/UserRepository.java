package com.example.empmgmt.repository;

import com.example.empmgmt.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    /**
     * 根据角色查询数量
     */
    long countByRole(String role);

    /**
     * 根据部门查询数量
     */
    long countByDepartment(String department);
}
