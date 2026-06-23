package com.rrs.repository;

import com.rrs.entity.Credential;
import com.rrs.entity.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    List<Credential> findByType(CredentialType type);

    List<Credential> findAllByOrderByCreatedAtDesc();
}
