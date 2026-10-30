package nl.logicai.wiki.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The internal username is derived from the e-mail address since nobody chooses one anymore (login is
 * by e-mail). Pure rules; the uniqueness suffix is covered by InvitationFlowIntegrationTest.
 */
class UserServiceUsernameTest {

	@Test
	void localPartLowerCasedKeepsDotsUnderscoresAndDashes() {
		assertThat(UserService.usernameBase("Ron.Cruz@LogicAI.nl")).isEqualTo("ron.cruz");
		assertThat(UserService.usernameBase("  ron_cruz-2@example.com ")).isEqualTo("ron_cruz-2");
	}

	@Test
	void otherCharactersAreDropped() {
		assertThat(UserService.usernameBase("ron+wiki@example.com")).isEqualTo("ronwiki");
		assertThat(UserService.usernameBase("rené van 't hof@example.com")).isEqualTo("renvanthof");
	}

	@Test
	void nothingUsableFallsBackToGebruiker() {
		assertThat(UserService.usernameBase("+++@example.com")).isEqualTo(UserService.USERNAME_FALLBACK);
		assertThat(UserService.usernameBase("@example.com")).isEqualTo(UserService.USERNAME_FALLBACK);
		assertThat(UserService.usernameBase(null)).isEqualTo(UserService.USERNAME_FALLBACK);
	}

	@Test
	void withoutAtSignTheWholeValueIsUsedAndCapped() {
		assertThat(UserService.usernameBase("admin")).isEqualTo("admin");
		assertThat(UserService.usernameBase("a".repeat(150) + "@example.com")).hasSize(100);
	}

}
