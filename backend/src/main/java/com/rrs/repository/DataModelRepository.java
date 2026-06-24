package com.rrs.repository;

import com.rrs.entity.DataModel;
import com.rrs.entity.DataModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataModelRepository extends JpaRepository<DataModel, Long> {

    Optional<DataModel> findByCode(String code);

    List<DataModel> findByLibraryId(Long libraryId);

    List<DataModel> findByLibraryIdAndStatus(Long libraryId, DataModelStatus status);
}
