package nl.logicai.wiki.mail;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;

/**
 * Replaces the SMTP/log sender in the mail-flow tests so they can read the link. Import it next to
 * {@code TestcontainersConfiguration}; {@link #SENT} is static, so clear it in a {@code @BeforeEach}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CapturedMail {

	/** One captured mail: {@code soort} is "reset" or "uitnodiging", {@code aan} the lower-cased address. */
	public record Sent(String soort, String aan, String link) {
	}

	public static final List<Sent> SENT = new CopyOnWriteArrayList<>();

	/** When set, every send fails with a {@link MailSendException} carrying this message (a refusing SMTP server). */
	public static volatile String FAIL_WITH = null;

	@Bean
	@Primary
	WikiMailSender capturingMailSender() {
		return new WikiMailSender() {
			@Override
			public void sendPasswordReset(WikiUser user, String link, Duration validFor) {
				record("reset", user.getEmail(), link);
			}

			@Override
			public void sendInvitation(String email, Role role, String invitedBy, String link, Duration validFor) {
				record("uitnodiging", email, link);
			}
		};
	}

	private static void record(String soort, String aan, String link) {
		String failure = FAIL_WITH;
		if (failure != null) {
			throw new MailSendException(failure);
		}
		SENT.add(new Sent(soort, aan, link));
	}

}
