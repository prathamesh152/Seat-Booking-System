package com.systemdesign.booking;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCheck implements CommandLineRunner {

    private final EventRepository eventRepository;

    public DatabaseCheck(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println(
                "Database event count: " + eventRepository.countEvents()
        );
    }
}