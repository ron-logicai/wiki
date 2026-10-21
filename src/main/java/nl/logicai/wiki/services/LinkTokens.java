package nl.logicai.wiki.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens for single-use links in e-mail ("wachtwoord vergeten", invitations): a random token goes into
 * the link, only its hex SHA-256 hash is stored (spec N-04).
 */
final class LinkTokens {

	private static final int TOKEN_BYTES = 32;
	private static final int MAX_LENGTH = 100;

	private LinkTokens() {
	}

	/** A fresh URL-safe token (43 characters). */
	static String generate(SecureRandom random) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Hex SHA-256 of the token; only this is stored. */
	static String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Whether a token from a URL or form is worth looking up at all. */
	static boolean plausible(String token) {
		return token != null && !token.isBlank() && token.length() <= MAX_LENGTH;
	}

}
