package vn.edu.fpt.ams.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class GeminiAssignmentClientTest {
    static String response(String output, String finish) {
        JsonObject part = new JsonObject(); part.addProperty("text", output);
        JsonArray parts = new JsonArray(); parts.add(part);
        JsonObject content = new JsonObject(); content.add("parts", parts);
        JsonObject candidate = new JsonObject(); candidate.add("content", content); candidate.addProperty("finishReason", finish);
        JsonArray candidates = new JsonArray(); candidates.add(candidate);
        JsonObject root = new JsonObject(); root.add("candidates", candidates); return root.toString();
    }

    @Test void missingKeyAndInvalidModelFailWithoutNetwork() {
        for (String key : new String[]{"", "CHANGE_ME"}) {
            var client = new GeminiAssignmentClient(key, GeminiAssignmentClient.DEFAULT_MODEL, request -> { fail("No network without key"); return null; });
            assertTrue(assertThrows(GeminiAssignmentClient.UnavailableException.class, () -> client.generate("prompt")).getMessage().contains("GEMINI_API_KEY"));
        }
        var invalid = new GeminiAssignmentClient("key", "../wrong?key=", request -> { fail("Invalid model must not be sent"); return null; });
        assertThrows(GeminiAssignmentClient.UnavailableException.class, () -> invalid.generate("prompt"));
        var invalidKey = new GeminiAssignmentClient("private\nsecret", "model", request -> { fail("Invalid key must not enter HTTP headers"); return null; });
        assertFalse(assertThrows(GeminiAssignmentClient.UnavailableException.class, () -> invalidKey.generate("prompt")).getMessage().contains("private"));
    }

    @Test void sendsOfficialEndpointHeaderAndStructuredSchemaAndReadsJson() throws Exception {
        String vietnameseQuestion = "Mô tả kiến trúc tổng quan của hệ thống.";
        String tasks = "{\"tasks\":[{\"title\":\"" + vietnameseQuestion + "\",\"maximum_points\":10,\"requirements\":[]}]}";
        var client = new GeminiAssignmentClient("server-secret", "configured-model", request -> {
            assertEquals("https://generativelanguage.googleapis.com/v1beta/models/configured-model:generateContent", request.uri().toString());
            assertEquals("server-secret", request.headers().firstValue("x-goog-api-key").orElseThrow());
            assertFalse(request.uri().toString().contains("server-secret"));
            assertTrue(request.timeout().orElseThrow().toSeconds() <= 60);
            CompletableFuture<String> collected = new CompletableFuture<>(); StringBuilder body = new StringBuilder();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
                public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(ByteBuffer buffer) { body.append(StandardCharsets.UTF_8.decode(buffer)); }
                public void onError(Throwable failure) { collected.completeExceptionally(failure); }
                public void onComplete() { collected.complete(body.toString()); }
            });
            JsonObject sent = JsonParser.parseString(collected.join()).getAsJsonObject();
            String instructions = sent.getAsJsonObject("systemInstruction").getAsJsonArray("parts")
                    .get(0).getAsJsonObject().get("text").getAsString();
            assertTrue(instructions.contains("đầy đủ dấu tiếng Việt"), "AI must be instructed to retain Vietnamese diacritics");
            assertTrue(instructions.contains("dấu câu"), "AI must be instructed to use punctuation");
            assertEquals("application/json", sent.getAsJsonObject("generationConfig").get("responseMimeType").getAsString());
            assertTrue(sent.getAsJsonObject("generationConfig").has("responseJsonSchema"));
            // Large nested maxItems limits caused HTTP 400 with the real provider.
            JsonObject array = sent.getAsJsonObject("generationConfig").getAsJsonObject("responseJsonSchema")
                    .getAsJsonObject("properties").getAsJsonObject("tasks");
            assertFalse(array.has("maxItems"));
            assertFalse(array.getAsJsonObject("items").getAsJsonObject("properties")
                    .getAsJsonObject("requirements").has("maxItems"));
            return new GeminiAssignmentClient.ProviderResponse(200, response(tasks, "STOP"));
        });
        JsonArray generated = client.generate("Mo ta kien truc tong quan cua he thong");
        assertEquals(1, generated.size());
        assertEquals(vietnameseQuestion, generated.get(0).getAsJsonObject().get("title").getAsString());
    }

    @Test void acceptsAuthorizationKeyFormatAndKeepsItInTheHeader() {
        String key = "AQ.synthetic-authorization_key.123";
        var client = new GeminiAssignmentClient(key, "model", request -> {
            assertEquals(key, request.headers().firstValue("x-goog-api-key").orElseThrow());
            assertFalse(request.uri().toString().contains(key));
            return new GeminiAssignmentClient.ProviderResponse(200, response("{\"tasks\":[{}]}", "STOP"));
        });
        assertEquals(1, client.generate("prompt").size());
    }

    @Test void malformedBlockedTruncatedAndOversizeProviderResponsesFailClosed() {
        for (String body : new String[]{"not json", "{}", response("{bad}", "STOP"), response("{\"tasks\":[]}", "STOP"), response("{\"tasks\":[{}]}", "MAX_TOKENS"), "a".repeat(512 * 1024 + 1)}) {
            var client = new GeminiAssignmentClient("key", "model", request -> new GeminiAssignmentClient.ProviderResponse(200, body));
            assertThrows(GeminiAssignmentClient.ProviderException.class, () -> client.generate("prompt"));
        }
    }

    @Test void quotaAuthenticationServerAndNetworkFailuresHaveSafeMessages() {
        for (int status : new int[]{401, 403, 429, 500}) {
            var client = new GeminiAssignmentClient("key", "model", request -> new GeminiAssignmentClient.ProviderResponse(status, "private-provider-details"));
            var failure = assertThrows(IllegalStateException.class, () -> client.generate("prompt"));
            assertFalse(failure.getMessage().contains("private-provider-details"));
            if (status == 500) assertInstanceOf(GeminiAssignmentClient.ProviderException.class, failure);
            else assertInstanceOf(GeminiAssignmentClient.UnavailableException.class, failure);
        }
        var network = new GeminiAssignmentClient("key", "model", request -> { throw new IOException("private details"); });
        assertThrows(GeminiAssignmentClient.UnavailableException.class, () -> network.generate("prompt"));
        var interrupt = new GeminiAssignmentClient("key", "model", request -> { throw new InterruptedException(); });
        assertThrows(GeminiAssignmentClient.UnavailableException.class, () -> interrupt.generate("prompt"));
        assertTrue(Thread.interrupted());
    }
}
