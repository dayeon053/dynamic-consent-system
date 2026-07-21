package com.consentradar.consentradar.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmClientTest {

    @Test
    void callWithPromptReturnsContentFromConfiguredApiResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"companyName\\\":\\\"테스트\\\"}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions";
            LlmClient client = new LlmClient("test-key", "gpt-4o-mini", baseUrl, true, HttpClient.newHttpClient(), new ObjectMapper());

            String result = client.callWithPrompt("hello");

            assertEquals("{\"companyName\":\"테스트\"}", result);
        } finally {
            server.stop(0);
        }
    }
}
