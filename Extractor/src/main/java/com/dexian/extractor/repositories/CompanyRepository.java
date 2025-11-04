package com.dexian.extractor.repositories;

import com.dexian.extractor.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Integer> {
}
