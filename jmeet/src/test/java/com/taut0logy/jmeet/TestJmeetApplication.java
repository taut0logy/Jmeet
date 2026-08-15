package com.taut0logy.jmeet;

import org.springframework.boot.SpringApplication;

public class TestJmeetApplication {

    public static void main(String[] args) {
        SpringApplication.from(JmeetApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
