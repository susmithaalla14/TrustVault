package com.trustvault.repository;

import com.trustvault.model.Document;
import com.trustvault.model.DocumentPermission;
import com.trustvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findByDocument(Document document);

    Optional<DocumentPermission> findByDocumentAndGrantedUser(Document document, User grantedUser);

    List<DocumentPermission> findByGrantedUserOrderByCreatedAtDesc(User grantedUser);

    boolean existsByDocumentAndGrantedUser(Document document, User grantedUser);

    void deleteByDocumentAndGrantedUser(Document document, User grantedUser);

    void deleteByDocument(Document document);
}
