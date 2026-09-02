package com.securevault.config;

import com.securevault.entity.User;
import com.securevault.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo account (demo / Demo@1234) on startup so evaluators can log in
 * immediately during a viva without registering. Controlled by the
 * {@code securevault.seed-demo-user} property.
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${securevault.seed-demo-user:true}")
    private boolean seedDemoUser;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!seedDemoUser) {
            return;
        }
        if (!users.existsByUsername("demo")) {
            users.save(new User("demo", encoder.encode("Demo@1234")));
            System.out.println("[SecureVault] Seeded demo user  ->  username: demo  password: Demo@1234");
        }
    }
}
