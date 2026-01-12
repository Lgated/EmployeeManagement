package com.example.empmgmt.repository;

import com.example.empmgmt.domain.ExportTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {

}
