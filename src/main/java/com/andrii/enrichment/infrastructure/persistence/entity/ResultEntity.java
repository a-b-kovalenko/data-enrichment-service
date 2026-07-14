package com.andrii.enrichment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "result")
public class ResultEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "message_id", nullable = false, unique = true)
  UUID messageId;

  @Column(name = "user_id", nullable = false)
  Long userId;

  @Column(nullable = false)
  String action;

  @Column(nullable = false)
  boolean result;

  @Column(name = "event_timestamp", nullable = false)
  Instant eventTimestamp;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;
}
