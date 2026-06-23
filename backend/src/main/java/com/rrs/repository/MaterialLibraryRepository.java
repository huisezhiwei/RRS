package com.rrs.repository;

import com.rrs.entity.LibraryType;
import com.rrs.entity.MaterialLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialLibraryRepository extends JpaRepository<MaterialLibrary, Long> {

    Optional<MaterialLibrary> findByCode(String code);

    List<MaterialLibrary> findByLibraryType(LibraryType type);

    @Query("SELECT m FROM MaterialLibrary m WHERE m.name LIKE %:keyword% OR m.code LIKE %:keyword%")
    List<MaterialLibrary> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT m FROM MaterialLibrary m WHERE m.libraryType = :type AND (m.name LIKE %:keyword% OR m.code LIKE %:keyword%)")
    List<MaterialLibrary> searchByTypeAndKeyword(@Param("type") LibraryType type, @Param("keyword") String keyword);
}
