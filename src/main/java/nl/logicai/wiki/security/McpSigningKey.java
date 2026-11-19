package nl.logicai.wiki.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RSA key that signs the access tokens of the MCP server. It comes from {@code wiki.mcp.signing-key}
 * (a PKCS8 private key in PEM form, on Fly a secret) and is deliberately not stored in the database: the
 * local profile and production share one database, and a key in a table would be readable by anyone who
 * can read that table. Without a configured key a new one is generated at every start, which is fine for
 * local use and tests but disconnects every agent on a restart.
 */
public final class McpSigningKey {

	private static final Logger log = LoggerFactory.getLogger(McpSigningKey.class);

	private McpSigningKey() {
	}

	public static RSAKey load(String pem) {
		try {
			if (pem == null || pem.isBlank()) {
				log.warn("wiki.mcp.signing-key is not set: using a temporary key, connected agents must log in again after a restart");
				KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
				generator.initialize(2048);
				KeyPair pair = generator.generateKeyPair();
				return jwk((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
			}
			// An environment variable often holds the PEM on one line, with literal "\n" between the parts.
			String base64 = pem.replace("\\n", "\n")
				.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
			KeyFactory factory = KeyFactory.getInstance("RSA");
			RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
			RSAPublicKey publicKey = (RSAPublicKey) factory
				.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
			return jwk(publicKey, privateKey);
		}
		catch (Exception e) {
			// Never echo the key material (spec N-04).
			throw new IllegalStateException("wiki.mcp.signing-key is not a valid RSA private key in PKCS8 PEM form "
				+ "(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048)");
		}
	}

	private static RSAKey jwk(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) throws Exception {
		return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
	}

}
