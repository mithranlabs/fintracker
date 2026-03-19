package com.mithran.fintracker.fin_backend.repository;

import com.mithran.fintracker.fin_backend.entity.MerchantRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface MerchantRuleRepository
        extends JpaRepository<MerchantRule, Integer> {

    Optional<MerchantRule> findByKeyword(String keyword);

}