package com.rrs.repository;

import com.rrs.entity.DataModelSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataModelScheduleRepository extends JpaRepository<DataModelSchedule, Long> {

    Optional<DataModelSchedule> findByModelId(Long modelId);

    List<DataModelSchedule> findByEnabledTrue();
}
