package com.andrii.enrichment.infrastructure.messaging;

import com.andrii.enrichment.infrastructure.configuration.EnrichmentMessagingProperties;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrichmentMessagingConfiguration {

  static final String DEAD_LETTER_EXCHANGE_ARGUMENT = "x-dead-letter-exchange";
  static final String DEAD_LETTER_ROUTING_KEY_ARGUMENT = "x-dead-letter-routing-key";
  static final String MESSAGE_TTL_ARGUMENT = "x-message-ttl";

  EnrichmentMessagingProperties properties;

  @Bean
  DirectExchange inputExchange() {
    return new DirectExchange(properties.inputExchange());
  }

  @Bean
  DirectExchange retryExchange() {
    return new DirectExchange(properties.retryExchange());
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(properties.deadLetterExchange());
  }

  @Bean
  Queue inputQueue() {
    return durableQueue(properties.inputQueue(), inputQueueArguments());
  }

  @Bean
  Queue retryQueue() {
    return durableQueue(properties.retryQueue(), retryQueueArguments());
  }

  @Bean
  Queue deadLetterQueue() {
    return new Queue(properties.deadLetterQueue(), true);
  }

  @Bean
  Binding inputBinding() {
    return BindingBuilder.bind(inputQueue())
      .to(inputExchange())
      .with(properties.inputRoutingKey());
  }

  @Bean
  Binding retryBinding() {
    return BindingBuilder.bind(retryQueue())
      .to(retryExchange())
      .with(properties.retryRoutingKey());
  }

  @Bean
  Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue())
      .to(deadLetterExchange())
      .with(properties.deadLetterRoutingKey());
  }

  private Queue durableQueue(String name, Map<String, Object> arguments) {
    return new Queue(name, true, false, false, arguments);
  }

  private Map<String, Object> inputQueueArguments() {
    return Map.of(
      DEAD_LETTER_EXCHANGE_ARGUMENT, properties.retryExchange(),
      DEAD_LETTER_ROUTING_KEY_ARGUMENT, properties.retryRoutingKey()
    );
  }

  private Map<String, Object> retryQueueArguments() {
    return Map.of(
      MESSAGE_TTL_ARGUMENT, properties.retryDelayMilliseconds(),
      DEAD_LETTER_EXCHANGE_ARGUMENT, properties.inputExchange(),
      DEAD_LETTER_ROUTING_KEY_ARGUMENT, properties.inputRoutingKey()
    );
  }
}
