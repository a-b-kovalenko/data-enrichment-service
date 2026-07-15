package com.andrii.enrichment.infrastructure.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public final class WireMockTestSupport {

  private WireMockTestSupport() {
  }

  public static WireMockServer startServer() {
    var server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    server.start();
    return server;
  }
}
