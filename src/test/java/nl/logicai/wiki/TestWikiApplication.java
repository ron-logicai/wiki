package nl.logicai.wiki;

import org.springframework.boot.SpringApplication;

public class TestWikiApplication {

	public static void main(String[] args) {
		SpringApplication.from(WikiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
