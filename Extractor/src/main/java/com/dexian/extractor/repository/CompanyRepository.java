package com.dexian.extractor.repository;

import com.dexian.extractor.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Integer> {

    Optional<Company> findBySecCikNumber(String secCikNumber);
}
