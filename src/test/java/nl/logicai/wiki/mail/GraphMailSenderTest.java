package nl.logicai.wiki.mail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nl.logicai.wiki.config.WikiMailProperties;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link GraphMailSender} against a fake Entra token endpoint and a fake Graph on localhost (JDK HttpServer, like
 * FakeEntra in the security tests): the mail goes out as the application from the sender mailbox, the token is
 * cached and renewed, and refusals of Entra or Graph surface as a {@link MailSendException} with their reason.
 */
class GraphMailSenderTest {

	private static final String SENDER = "wiki@logicai.nl";
	private static final String TOKEN_PATH = "/test-tenant/oauth2/v2.0/token";
	private static final String SEND_PATH = "/v1.0/users/" + SENDER + "/sendMail";
	private static final String LINK = "http://localhost:8080/accountaanmaken?token=abc123";

	/** One request the fake server received. */
	record Request(String path, String authorization, String contentType, String body) {
	}

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private volatile int tokenStatus = 200;
	private volatile String tokenBody = "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"t1\"}";
	private volatile int sendStatus = 202;
	private volatile String sendBody = "";
	/** When set, the next sendMail answers with this status once, then {@link #sendStatus} again. */
	private volatile Integer sendStatusOnce;

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-21T10:00:00Z"));

	private HttpServer server;
	private GraphMailSender sender;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", this::handle);
		server.start();
		String base = "http://localhost:" + server.getAddress().getPort();
		sender = new GraphMailSender(RestClient.builder(),
				new WikiMailProperties.Graph(SENDER, "test-tenant", "client-123", "geheim", base + "/", base + "/v1.0"), clock);
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void invitationGoesOutAsTheApplicationFromTheSenderMailbox() {
		sender.sendInvitation("nieuw@example.com", Role.EDITOR, "admin", LINK, Duration.ofHours(1));

		assertThat(requests).hasSize(2);
		Request token = requests.get(0);
		assertThat(token.path()).isEqualTo(TOKEN_PATH);
		assertThat(token.contentType()).startsWith("application/x-www-form-urlencoded");
		assertThat(token.body()).contains("grant_type=client_credentials").contains("client_id=client-123")
			.contains("client_secret=geheim").contains("scope=https%3A%2F%2Fgraph.microsoft.com%2F.default");

		Request send = requests.get(1);
		assertThat(send.path()).isEqualTo(SEND_PATH);
		assertThat(send.authorization()).isEqualTo("Bearer t1");
		assertThat(send.contentType()).startsWith("application/json");
		assertThat(send.body()).contains("\"address\":\"nieuw@example.com\"")
			.contains("\"subject\":\"LogicAI Wiki: uitnodiging voor een account\"")
			.contains("\"contentType\":\"Text\"")
			.contains("admin heeft je uitgenodigd").contains("Editor").contains(LINK)
			.contains("\"saveToSentItems\":false");
	}

	@Test
	void passwordResetUsesTheSameMailboxAndTexts() {
		WikiUser user = WikiUser.create("ron", "Ron", "ron@example.com", "hash", Role.ADMIN, "test", clock.instant());
		sender.sendPasswordReset(user, "http://localhost:8080/wachtwoord-herstellen?token=xyz", Duration.ofMinutes(30));

		Request send = requests.get(1);
		assertThat(send.path()).isEqualTo(SEND_PATH);
		assertThat(send.body()).contains("\"address\":\"ron@example.com\"")
			.contains("\"subject\":\"LogicAI Wiki: nieuw wachtwoord instellen\"")
			.contains("Hallo Ron").contains("30 minuten").contains("wachtwoord-herstellen?token=xyz");
	}

	@Test
	void tokenIsReusedUntilShortlyBeforeItExpires() {
		sender.sendInvitation("a@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1));
		sender.sendInvitation("b@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1));
		assertThat(tokenRequests()).isEqualTo(1);

		// 3600 s minus the 60 s margin: just inside the window, still the same token.
		clock.advance(Duration.ofSeconds(3600 - 61));
		sender.sendInvitation("c@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1));
		assertThat(tokenRequests()).isEqualTo(1);

		clock.advance(Duration.ofSeconds(2));
		tokenBody = "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"t2\"}";
		sender.sendInvitation("d@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1));
		assertThat(tokenRequests()).isEqualTo(2);
		assertThat(requests.getLast().authorization()).isEqualTo("Bearer t2");
	}

	@Test
	void rejectedTokenIsRenewedOnce() {
		sender.sendInvitation("a@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1));
		tokenBody = "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"t2\"}";
		sendStatusOnce = 401;

		sender.sendInvitation("b@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1));

		assertThat(tokenRequests()).isEqualTo(2);
		List<Request> sends = requests.stream().filter(r -> r.path().equals(SEND_PATH)).toList();
		assertThat(sends).hasSize(3);
		assertThat(sends.get(1).authorization()).isEqualTo("Bearer t1");
		assertThat(sends.get(2).authorization()).isEqualTo("Bearer t2");
	}

	@Test
	void graphRefusalBecomesMailSendExceptionWithGraphsReason() {
		sendStatus = 403;
		sendBody = "{\"error\":{\"code\":\"ErrorAccessDenied\",\"message\":\"Access is denied. Check credentials and try again.\","
				+ "\"innerError\":{\"date\":\"2026-10-21T10:00:00\",\"request-id\":\"r1\"}}}";

		assertThatThrownBy(() -> sender.sendInvitation("a@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1)))
			.isInstanceOf(MailSendException.class)
			.hasMessageContaining("Microsoft Graph weigerde de mail (403)")
			.hasMessageContaining("ErrorAccessDenied: Access is denied. Check credentials and try again.");
	}

	@Test
	void entraRefusalBecomesMailSendExceptionWithoutCallingGraph() {
		tokenStatus = 401;
		tokenBody = "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client secret provided.\\r\\n"
				+ "Trace ID: t\",\"error_codes\":[7000215]}";

		assertThatThrownBy(() -> sender.sendInvitation("a@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1)))
			.isInstanceOf(MailSendException.class)
			.hasMessageContaining("Entra gaf geen token voor Microsoft Graph (401)")
			.hasMessageContaining("invalid_client: AADSTS7000215: Invalid client secret provided.");
		assertThat(requests).allMatch(r -> r.path().equals(TOKEN_PATH));
	}

	@Test
	void unreachableEntraBecomesMailSendException() {
		server.stop(0);

		assertThatThrownBy(() -> sender.sendInvitation("a@example.com", Role.VIEWER, "admin", LINK, Duration.ofHours(1)))
			.isInstanceOf(MailSendException.class)
			.hasMessageContaining("Entra is niet bereikbaar");
	}

	@Test
	void reasonFallsBackToTheRawBody() {
		assertThat(GraphMailSender.reasonOf(null)).isEqualTo("geen foutmelding.");
		assertThat(GraphMailSender.reasonOf("Service Unavailable")).isEqualTo("Service Unavailable");
		assertThat(GraphMailSender.reasonOf("{\"error\":{\"code\":\"MailboxNotEnabledForRESTAPI\"}}"))
			.isEqualTo("MailboxNotEnabledForRESTAPI.");
	}

	private long tokenRequests() {
		return requests.stream().filter(r -> r.path().equals(TOKEN_PATH)).count();
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		requests.add(new Request(path, exchange.getRequestHeaders().getFirst("Authorization"),
				exchange.getRequestHeaders().getFirst("Content-Type"), body));
		if (path.equals(TOKEN_PATH)) {
			respond(exchange, tokenStatus, tokenBody);
		}
		else if (path.equals(SEND_PATH)) {
			Integer once = sendStatusOnce;
			sendStatusOnce = null;
			respond(exchange, once != null ? once : sendStatus, sendBody);
		}
		else {
			respond(exchange, 404, "{\"error\":{\"code\":\"NotFound\",\"message\":\"" + path + "\"}}");
		}
	}

	private static void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	/** A clock the test moves forward. */
	private static final class MutableClock extends Clock {

		private volatile Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
