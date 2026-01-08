package com.example.empmgmt.dto.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工导出VO
 * 使用EasyExcel注解定义导出格式
 */
@Data
@HeadRowHeight(25)  // 表头行高
@ContentRowHeight(20)  // 内容行高
public class EmployeeExportVO {

    @ExcelProperty(value = "ID", index = 0) // 列名和索引
    @ColumnWidth(10) // 列宽
    private Long id;

    @ExcelProperty(value = "姓名", index = 1)
    @ColumnWidth(15)
    private String name;

    @ExcelProperty(value = "性别", index = 2)
    @ColumnWidth(10)
    private String gender;

    @ExcelProperty(value = "年龄", index = 3)
    @ColumnWidth(10)
    private Integer age;

    @ExcelProperty(value = "部门", index = 4)
    @ColumnWidth(20)
    private String department;

    @ExcelProperty(value = "职位", index = 5)
    @ColumnWidth(20)
    private String position;

    @ExcelProperty(value = "入职日期", index = 6)
    @ColumnWidth(15)
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate hireDate;

    @ExcelProperty(value = "薪资", index = 7)
    @ColumnWidth(15)
    private BigDecimal salary;

    @ExcelProperty(value = "头像URL", index = 8)
    @ColumnWidth(40)
    private String avatar;

    @ExcelProperty(value = "创建时间", index = 9)
    @ColumnWidth(20)
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @ExcelProperty(value = "更新时间", index = 10)
    @ColumnWidth(20)
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

}
