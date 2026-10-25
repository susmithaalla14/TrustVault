package com.trustvault.repository;

import com.trustvault.model.Document;
import com.trustvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwnerOrderByCreatedAtDesc(User owner);

    List<Document> findAllByOrderByCreatedAtDesc();

    Optional<Document> findByStorageObjectKey(String storageObjectKey);

    long countByOwner(User owner);

    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0L) FROM Document d WHERE d.owner = :owner")
    long getTotalStorageUsedByOwner(@Param("owner") User owner);

    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0L) FROM Document d")
    long getTotalStorageUsedSystemWide();

    /**
     * Find documents shared with a specific user.
     */
    @Query("SELECT d FROM Document d JOIN d.permissions p WHERE p.grantedUser = :user ORDER BY d.createdAt DESC")
    List<Document> findSharedWithUser(@Param("user") User user);

    /**
     * Search documents owned by a user with keyword (filename/title) and category filter.
     */
    @Query("SELECT d FROM Document d WHERE d.owner = :owner " +
           "AND (:category IS NULL OR :category = '' OR d.category = :category) " +
           "AND (:query IS NULL OR :query = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY d.createdAt DESC")
    List<Document> searchOwnedDocuments(@Param("owner") User owner,
                                        @Param("query") String query,
                                        @Param("category") String category);

    /**
     * System-wide search for ADMIN role.
     */
    @Query("SELECT d FROM Document d WHERE " +
           "(:category IS NULL OR :category = '' OR d.category = :category) " +
           "AND (:query IS NULL OR :query = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(d.owner.username) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY d.createdAt DESC")
    List<Document> searchAllDocumentsAdmin(@Param("query") String query,
                                          @Param("category") String category);
}
