package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.Upload;
import com.mithran.fintracker.fin_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadRepository extends JpaRepository<Upload, Integer> {
    List<Upload> findByUserOrderByUploadDateDesc(User user);
}