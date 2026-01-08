package com.example.empmgmt.dto.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户导出VO
 */
@Data
@HeadRowHeight(25)
@ContentRowHeight(20)
public class UserExportVO {

    @ExcelProperty(value = "ID", index = 0)
    @ColumnWidth(10)
    private Long id;

    @ExcelProperty(value = "用户名", index = 1)
    @ColumnWidth(20)
    private String username;

    @ExcelProperty(value = "邮箱", index = 2)
    @ColumnWidth(30)
    private String email;

    @ExcelProperty(value = "角色", index = 3)
    @ColumnWidth(15)
    private String role;

    @ExcelProperty(value = "部门", index = 4)
    @ColumnWidth(20)
    private String department;

    @ExcelProperty(value = "关联员工ID", index = 5)
    @ColumnWidth(15)
    private Long employeeId;

    @ExcelProperty(value = "启用状态", index = 6)
    @ColumnWidth(15)
    private String enabledStatus;

    @ExcelProperty(value = "创建时间", index = 7)
    @ColumnWidth(20)
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @ExcelProperty(value = "更新时间", index = 8)
    @ColumnWidth(20)
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

}
