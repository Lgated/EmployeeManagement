package com.example.empmgmt.service.Impl;

import com.alibaba.excel.EasyExcel;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.vo.EmployeeExportVO;
import com.example.empmgmt.dto.vo.UserExportVO;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    // 文件名时间格式化器
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public ExportServiceImpl(EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void exportEmployeesToExcel(String department, String position, HttpServletResponse response) throws IOException {
        //1、查询数据
        List<Employee> employee;
        if(department != null && !department.isEmpty()){
            if(position != null && !position.isEmpty()){
                employee = employeeRepository.findByDepartmentAndPositionAndDeletedFalse(department, position);
            }else{
                employee = employeeRepository.findByDepartmentAndDeletedFalse(department);
            }
        }else {
            // 无过滤条件，查询所有未删除的员工
            employee = employeeRepository.findAllByDeletedFalse();
        }
        
        //2、转换为VO
        List<EmployeeExportVO> voList = employee.stream().map(this::convertToEmployeeVO).toList();

        //3、设置响应头
        String fileName = "员工信息_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20"));
        // 4. 使用EasyExcel写入
        EasyExcel.write(response.getOutputStream(), EmployeeExportVO.class)
                .sheet("员工信息")
                .doWrite(voList);
    }

    @Override
    public void exportUsersToExcel(String role, String department, HttpServletResponse response) throws IOException {
        //1、查询数据
        List<User> users;
        if(role != null && !role.isEmpty()){
            if(department != null && !department.isEmpty()){
                users = userRepository.findByRoleAndDepartment(role, department);
            }else{
                users = userRepository.findByRole(role);
            }
        }else {
            // 无过滤条件，查询所有用户
            users = userRepository.findAll();
        }

        //2、转换为VO
        List<UserExportVO> voList = users.stream().map(this::convertToUserVO).collect(Collectors.toList());

        //3、设置响应头
        String fileName = "用户信息_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20"));
        // 4. 使用EasyExcel写入
        EasyExcel.write(response.getOutputStream(), UserExportVO.class)
                .sheet("用户信息")
                .doWrite(voList);
    }

    /**
     * Employee转换为EmployeeExportVO
     */
    private EmployeeExportVO convertToEmployeeVO(Employee employee) {
        EmployeeExportVO vo = new EmployeeExportVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setGender(employee.getGender());
        vo.setAge(employee.getAge());
        vo.setDepartment(employee.getDepartment());
        vo.setPosition(employee.getPosition());
        vo.setHireDate(employee.getHireDate());
        vo.setSalary(employee.getSalary());
        vo.setAvatar(employee.getAvatar());
        vo.setCreatedAt(employee.getCreatedAt());
        vo.setUpdatedAt(employee.getUpdatedAt());
        return vo;
    }

    /**
     * User转换为UserExportVO
     */
    private UserExportVO convertToUserVO(User user) {
        UserExportVO vo = new UserExportVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setDepartment(user.getDepartment());
        vo.setEmployeeId(user.getEmployeeId());
        vo.setEnabledStatus(user.getEnabled() != null && user.getEnabled() ? "启用" : "禁用");
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
