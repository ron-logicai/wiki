package nl.logicai.wiki.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for Microsoft Entra ID so the OIDC login flow can be tested end to end without the
 * internet: a token endpoint that answers the authorization-code exchange with a signed ID token,
 * and a JWKS endpoint with the public key Spring uses to verify it. The authorization endpoint is
 * never called; the test reads the redirect Spring builds and plays the browser coming back.
 */
final class FakeEntra {

	static final String ISSUER = "https://login.microsoftonline.com/test-tenant/v2.0";

	private final HttpServer server;

	private final RSAKey key;

	private final String clientId;

	private volatile Map<String, Object> nextClaims = Map.of();

	private volatile Map<String, String> lastTokenRequest = Map.of();

	private FakeEntra(HttpServer server, RSAKey key, String clientId) {
		this.server = server;
		this.key = key;
		this.clientId = clientId;
	}

	static FakeEntra start(String clientId) {
		try {
			RSAKey key = new RSAKeyGenerator(2048).keyID("wiki-test-key").generate();
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			FakeEntra entra = new FakeEntra(server, key, clientId);
			server.createContext("/token", entra::token);
			server.createContext("/jwks", entra::jwks);
			server.start();
			return entra;
		}
		catch (IOException | JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	void stop() {
		server.stop(0);
	}

	String url(String path) {
		return "http://localhost:" + server.getAddress().getPort() + path;
	}

	/** Claims for the next ID token; {@code nonce} must be the value from the authorization redirect. */
	void nextIdToken(Map<String, Object> claims) {
		this.nextClaims = new LinkedHashMap<>(claims);
	}

	/** The form fields of the last authorization-code exchange (grant_type, code, redirect_uri, ...). */
	Map<String, String> lastTokenRequest() {
		return lastTokenRequest;
	}

	private void token(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		lastTokenRequest = parseForm(body);
		String json = "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
			+ "\"scope\":\"openid profile email\",\"id_token\":\"" + signIdToken() + "\"}";
		respond(exchange, json);
	}

	private void jwks(HttpExchange exchange) throws IOException {
		respond(exchange, new JWKSet(key.toPublicJWK()).toString());
	}

	private String signIdToken() {
		Instant now = Instant.now();
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
			.issuer(ISSUER)
			.subject("entra-subject")
			.audience(List.of(clientId))
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(300)));
		nextClaims.forEach(claims::claim);
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
					claims.build());
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void respond(HttpExchange exchange, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	static Map<String, String> parseForm(String body) {
		Map<String, String> fields = new HashMap<>();
		if (body.isEmpty()) {
			return fields;
		}
		for (String pair : body.split("&")) {
			String[] keyValue = pair.split("=", 2);
			String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
			fields.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8), value);
		}
		return fields;
	}

}
