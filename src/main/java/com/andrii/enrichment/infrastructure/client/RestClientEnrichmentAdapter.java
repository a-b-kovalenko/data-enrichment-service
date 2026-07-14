package com.andrii.enrichment.infrastructure.client;

import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import com.andrii.enrichment.application.port.ExternalEnrichmentPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.SocketTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RestClientEnrichmentAdapter implements ExternalEnrichmentPort {

  RestClient enrichmentRestClient;
  CircuitBreaker enrichmentApiCircuitBreaker;

  @Override
  public EnrichmentResponse enrich(EnrichmentRequest request) {
    log.info("Calling enrichment API for userId={}", request.userId());

    try {
      return enrichmentApiCircuitBreaker.executeSupplier(() -> callExternalApi(request));
    } catch (CallNotPermittedException exception) {
      throw new RetryableEnrichmentClientException("Enrichment API circuit is open", exception);
    } catch (RetryableEnrichmentClientException | NonRetryableEnrichmentClientException exception) {
      throw exception;
    } catch (ResourceAccessException exception) {
      throw new RetryableEnrichmentClientException("Enrichment API is unavailable", exception);
    } catch (RestClientException exception) {
      if (SocketTimeoutException.class.isInstance(NestedExceptionUtils.getMostSpecificCause(exception))) {
        throw new RetryableEnrichmentClientException("Enrichment API is unavailable", exception);
      }
      throw new NonRetryableEnrichmentClientException("Enrichment API response is invalid", exception);
    }
  }

  private EnrichmentResponse callExternalApi(EnrichmentRequest request) {
    var externalResponse = enrichmentRestClient.post()
      .uri("/enrich")
      .body(request)
      .retrieve()
      .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, clientResponse) -> {
        throw new NonRetryableEnrichmentClientException("Enrichment API rejected the request");
      })
      .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, clientResponse) -> {
        throw new RetryableEnrichmentClientException("Enrichment API returned a server error");
      })
      .body(EnrichmentResponse.class);

    return validateAndMapResponse(request, externalResponse);
  }

  private EnrichmentResponse validateAndMapResponse(
    EnrichmentRequest request, EnrichmentResponse externalResponse
  ) {
    if (externalResponse == null || externalResponse.userId() == null || externalResponse.result() == null) {
      throw new NonRetryableEnrichmentClientException("Enrichment API returned an incomplete response");
    }
    if (!request.userId().equals(externalResponse.userId())) {
      throw new NonRetryableEnrichmentClientException("Enrichment API returned a mismatched userId");
    }
    return externalResponse;
  }

}
