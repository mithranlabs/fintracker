package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Upload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, Integer> {
}