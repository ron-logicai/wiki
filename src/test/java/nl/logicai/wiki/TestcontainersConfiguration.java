package nl.logicai.wiki;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** The same MySQL version as the real server, with the UTC parameters every wiki URL carries (application.yaml). */
	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:9.1"))
			.withUrlParam("connectionTimeZone", "UTC")
			.withUrlParam("forceConnectionTimeZoneToSession", "true");
	}

}
