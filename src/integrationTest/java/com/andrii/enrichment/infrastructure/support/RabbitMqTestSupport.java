package com.andrii.enrichment.infrastructure.support;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.RabbitMQContainer;

public final class RabbitMqTestSupport {

  private RabbitMqTestSupport() {
  }

  public static void purgeQueues(RabbitAdmin rabbitAdmin, String... queueNames) {
    for (var queueName : queueNames) {
      rabbitAdmin.purgeQueue(queueName, false);
    }
  }

  public static void registerContainerProperties(DynamicPropertyRegistry registry, RabbitMQContainer rabbitMq) {
    registry.add("spring.rabbitmq.host", rabbitMq::getHost);
    registry.add("spring.rabbitmq.port", rabbitMq::getAmqpPort);
    registry.add("spring.rabbitmq.username", rabbitMq::getAdminUsername);
    registry.add("spring.rabbitmq.password", rabbitMq::getAdminPassword);
  }
}
