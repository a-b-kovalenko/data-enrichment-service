package com.andrii.enrichment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "message_id", nullable = false)
  UUID messageId;

  @Column(nullable = false, columnDefinition = "TEXT")
  String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  OutboxEventStatus status = OutboxEventStatus.PENDING;

  @Column(name = "attempt_count", nullable = false)
  @Builder.Default
  Integer attemptCount = 0;

  @Column(name = "next_attempt_at", nullable = false)
  Instant nextAttemptAt;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "published_at")
  Instant publishedAt;
}
