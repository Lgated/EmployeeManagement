package com.example.empmgmt.repository;

import com.example.empmgmt.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long>, JpaSpecificationExecutor<User> {

    /**
     * 根据用户名查询用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据角色查询数量
     */
    long countByRole(String role);

    /**
     * 根据部门查询数量
     */
    long countByDepartment(String department);

    // ========== 新增：根据员工ID查找用户 ==========
    /**
     * 根据员工ID查找用户（使用JPA关联）
     */
    List<User> findByEmployeeId(Long employeeId);

    /**
     * 根据员工ID查找用户（使用JPQL连表查询）
     */
    @Query("SELECT u FROM User u WHERE u.employee.id = :employeeId")
    List<User> findByEmployeeIdUsingJoin(@Param("employeeId") Long employeeId);

    /**
     * 根据员工姓名查找用户（JPQL连表查询）
     */
    @Query("SELECT u FROM User u JOIN u.employee e WHERE e.name = :employeeName AND e.deleted = false")
    Optional<User> findByEmployeeName(@Param("employeeName") String employeeName);

    // ========== 新增：查找所有用户并加载员工信息（避免N+1查询） ==========
    /**
     * 查找所有用户并立即加载员工信息（使用JOIN FETCH）
     *
     * 注意：JOIN FETCH 不能与分页一起使用，如果需要分页，使用 Specification
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.employee e WHERE e.deleted = false OR e IS NULL")
    List<User> findAllWithEmployee();

    // ========== 新增：根据部门查找用户（连表查询） ==========
    /**
     * 根据员工部门查找用户（JPQL连表查询）
     */
    @Query("SELECT u FROM User u JOIN u.employee e WHERE e.department = :department AND e.deleted = false")
    List<User> findByEmployeeDepartment(@Param("department") String department);

    // 根据角色和部门查找用户
    List<User> findByRoleAndDepartment(String role, String department);

    // 根据角色查找用户
    List<User> findByRole(String role);
}

