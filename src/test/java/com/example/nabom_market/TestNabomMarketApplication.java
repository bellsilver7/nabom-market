package com.example.nabom_market;

import org.springframework.boot.SpringApplication;

public class TestNabomMarketApplication {

	public static void main(String[] args) {
		SpringApplication.from(NabomMarketApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
