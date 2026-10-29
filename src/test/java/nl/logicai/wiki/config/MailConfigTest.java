package nl.logicai.wiki.config;

import nl.logicai.wiki.mail.GraphMailSender;
import nl.logicai.wiki.mail.LogMailSender;
import nl.logicai.wiki.mail.MailTemplates;
import nl.logicai.wiki.mail.SmtpMailSender;
import nl.logicai.wiki.mail.WikiMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

/** Which {@link WikiMailSender} {@link MailConfig} picks: Graph before SMTP before the log, and a clear error for half a Graph setup. */
class MailConfigTest {

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(WikiMailProperties.class)
	static class Properties {

		/** The senders need the mail templates; a bare engine is enough, nothing is rendered here. */
		@Bean
		MailTemplates mailTemplates() {
			return new MailTemplates(new SpringTemplateEngine());
		}
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(Properties.class, ClockConfig.class, MailConfig.class)
		.withPropertyValues("wiki.mail.port=587", "wiki.mail.starttls=true", "wiki.mail.from=wiki@logicai.nl",
				"wiki.mail.graph.login-base=https://login.microsoftonline.com",
				"wiki.mail.graph.api-base=https://graph.microsoft.com/v1.0");

	@Test
	void graphSenderWinsOverSmtp() {
		runner.withPropertyValues("wiki.mail.graph.sender=wiki@logicai.nl", "wiki.mail.graph.tenant-id=t",
				"wiki.mail.graph.client-id=c", "wiki.mail.graph.client-secret=s", "wiki.mail.host=smtp.office365.com")
			.run(context -> {
				assertThat(context).hasSingleBean(WikiMailSender.class);
				assertThat(context.getBean(WikiMailSender.class)).isInstanceOf(GraphMailSender.class);
				assertThat(context.getBean(WikiMailProperties.class).configured()).isTrue();
			});
	}

	@Test
	void smtpWhenOnlyHostIsSet() {
		runner.withPropertyValues("wiki.mail.host=smtp.gmail.com", "wiki.mail.username=u", "wiki.mail.password=p")
			.run(context -> assertThat(context.getBean(WikiMailSender.class)).isInstanceOf(SmtpMailSender.class));
	}

	@Test
	void logFallbackWithoutAnyMailSettings() {
		runner.run(context -> {
			assertThat(context.getBean(WikiMailSender.class)).isInstanceOf(LogMailSender.class);
			assertThat(context.getBean(WikiMailProperties.class).configured()).isFalse();
		});
	}

	@Test
	void graphSenderWithoutEntraSettingsFailsAtStartup() {
		runner.withPropertyValues("wiki.mail.graph.sender=wiki@logicai.nl", "wiki.mail.graph.client-id=c")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.hasMessageContaining("WIKI_MAIL_GRAPH_SENDER vereist WIKI_ENTRA_TENANT_ID, WIKI_ENTRA_CLIENT_SECRET");
			});
	}

	@Test
	void secretsStayOutOfToString() {
		runner.withPropertyValues("wiki.mail.host=h", "wiki.mail.password=smtp-geheim", "wiki.mail.graph.client-secret=graph-geheim")
			.run(context -> assertThat(context.getBean(WikiMailProperties.class).toString())
				.doesNotContain("smtp-geheim").doesNotContain("graph-geheim").contains("<verborgen>"));
	}

}
