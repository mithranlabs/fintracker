package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRuleRepository
        extends JpaRepository<MerchantRule, Integer> {

    MerchantRule findByKeyword(String keyword);

}