package com.andrii.enrichment.infrastructure.persistence.repository;

import com.andrii.enrichment.infrastructure.persistence.entity.ResultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultRepository extends JpaRepository<ResultEntity, Long> {

  Optional<ResultEntity> findByMessageId(UUID messageId);
}
