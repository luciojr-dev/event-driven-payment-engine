package com.luciojr.payment.repository;

import com.luciojr.payment.domain.DlqRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DlqRecordRepository extends JpaRepository<DlqRecord, Long> {
    List<DlqRecord> findAllByOrderByCreatedAtDesc();
}
