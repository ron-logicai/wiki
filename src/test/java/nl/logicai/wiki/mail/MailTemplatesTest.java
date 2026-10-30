package nl.logicai.wiki.mail;

import java.time.Duration;
import java.time.Instant;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

/** The two mails out of {@code templates/mail/}: link, names and validity in both bodies, and nothing unescaped. */
class MailTemplatesTest {

	private static final String LINK = "http://localhost:8080/accountaanmaken?token=abc_123-XYZ";

	private final MailTemplates templates = new MailTemplates(engine());

	/** The same resolver the app uses, without a Spring context (like PdfExporterTest). */
	static SpringTemplateEngine engine() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setCharacterEncoding("UTF-8");
		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(resolver);
		return engine;
	}

	@Test
	void invitationHasButtonLinkTextLinkNamesAndValidity() {
		MailTemplates.Rendered mail = templates.invitation(Role.EDITOR, "Ellen", LINK, Duration.ofHours(1));

		assertThat(mail.subject()).isEqualTo("LogicAI Wiki: uitnodiging voor een account");
		assertThat(mail.html())
			.contains("href=\"" + LINK + "\"")
			.contains(">" + LINK + "</a>")
			.contains("Account aanmaken")
			.contains("<span>Ellen</span> heeft een account als <strong>Editor</strong>")
			.contains("<strong>1 uur</strong> geldig en werkt één keer")
			.contains("Groet,<br><strong>Ellen</strong>")
			.contains("Je bent uitgenodigd voor de wiki")
			.contains("LogicAI · Zeewolde")
			.doesNotContain("th:text").doesNotContain("th:replace").doesNotContain("xmlns:th");
		assertThat(mail.text())
			.contains("Ellen heeft een account als Editor")
			.contains("1 uur geldig en werkt één keer")
			.contains(LINK)
			.doesNotContain("<");
	}

	@Test
	void namesFromOutsideAreEscapedInTheHtml() {
		MailTemplates.Rendered mail = templates.invitation(Role.VIEWER, "<b>x</b>", LINK + "&amp;\"", Duration.ofMinutes(45));

		assertThat(mail.html()).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>")
			.contains("45 minuten")
			.doesNotContain("token=abc_123-XYZ&amp;\"");
	}

	@Test
	void passwordResetNamesTheAccount() {
		WikiUser user = WikiUser.create("ron", "Ron", "ron@example.com", null, Role.ADMIN, "test", Instant.EPOCH);
		String link = "http://localhost:8080/wachtwoord-herstellen?token=xyz";

		MailTemplates.Rendered mail = templates.passwordReset(user, link, Duration.ofMinutes(30));

		assertThat(mail.subject()).isEqualTo("LogicAI Wiki: nieuw wachtwoord instellen");
		// The account is named by its e-mail address (the login), never by the internal username.
		assertThat(mail.html()).contains("Hoi Ron,").contains("<strong>ron@example.com</strong>").contains("Nieuw wachtwoord kiezen")
			.contains("href=\"" + link + "\"").contains("<strong>30 minuten</strong>")
			.contains("je wachtwoord blijft ongewijzigd").doesNotContain("<strong>ron</strong>");
		assertThat(mail.text()).contains("Hoi Ron,").contains("“ron@example.com”").contains("30 minuten").contains(link);
	}

	@Test
	void validityReadsNaturally() {
		assertThat(MailTemplates.validity(Duration.ofMinutes(60))).isEqualTo("1 uur");
		assertThat(MailTemplates.validity(Duration.ofHours(2))).isEqualTo("2 uur");
		assertThat(MailTemplates.validity(Duration.ofDays(1))).isEqualTo("1 dag");
		assertThat(MailTemplates.validity(Duration.ofDays(7))).isEqualTo("7 dagen");
		assertThat(MailTemplates.validity(Duration.ofMinutes(45))).isEqualTo("45 minuten");
		assertThat(MailTemplates.validity(Duration.ofMinutes(1))).isEqualTo("1 minuut");
		assertThat(MailTemplates.validity(Duration.ofSeconds(10))).isEqualTo("1 minuut");
	}

}
