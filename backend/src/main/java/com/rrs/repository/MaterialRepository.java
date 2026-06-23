package com.rrs.repository;

import com.rrs.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    @EntityGraph(attributePaths = {"tags"})
    Optional<Material> findById(Long id);

    @EntityGraph(attributePaths = {"tags"})
    Page<Material> findByLibraryId(Long libraryId, Pageable pageable);

    long countByLibraryId(Long libraryId);

    Optional<Material> findTopByLibraryIdOrderByUploadedAtDesc(Long libraryId);

    @EntityGraph(attributePaths = {"tags"})
    @Query("SELECT m FROM Material m JOIN m.tags t WHERE m.library.id = :libraryId AND t.name = :tagName")
    List<Material> findByLibraryIdAndTagName(@Param("libraryId") Long libraryId, @Param("tagName") String tagName);

    @EntityGraph(attributePaths = {"tags"})
    List<Material> findByLibraryId(Long libraryId);
}
