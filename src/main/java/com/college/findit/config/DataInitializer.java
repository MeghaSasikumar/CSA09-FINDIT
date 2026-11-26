package com.college.findit.config;

import com.college.findit.model.AppUser;
import com.college.findit.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin (and two demo students) when the database is empty. */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${findit.admin-password}")
    private String adminPassword;

    @Value("${findit.allowed-email-domain}")
    private String domain;

    @Value("${findit.seed-demo:}")
    private String seedDemo;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    public DataInitializer(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        boolean local = dbUrl.startsWith("jdbc:h2");
        boolean seed = seedDemo.isBlank() ? local : Boolean.parseBoolean(seedDemo.trim());

        if (!users.existsByRole(AppUser.Role.ADMIN)) {
            if (!local && (adminPassword.equals("admin123") || adminPassword.length() < 8)) {
                throw new IllegalStateException("Refusing to start on a cloud database with a weak admin password. "
                        + "Set the ADMIN_PASSWORD environment variable (at least 8 characters, not admin123).");
            }
            AppUser admin = new AppUser();
            admin.setRollNo("ADMIN");
            admin.setName("Campus Admin");
            admin.setEmail("admin@saveetha.com");
            admin.setDepartment("Administration");
            admin.setRole(AppUser.Role.ADMIN);
            admin.setPasswordHash(encoder.encode(adminPassword));
            admin.setMustChangePassword(false);
            users.save(admin);
        }
        if (seed) {
            seedStudent("DEMO1", "Demo Student One");
            seedStudent("DEMO2", "Demo Student Two");
        }
    }

    private void seedStudent(String roll, String name) {
        if (users.existsByRollNo(roll)) return;
        AppUser s = new AppUser();
        s.setRollNo(roll);
        s.setName(name);
        s.setEmail(roll.toLowerCase() + "@" + domain);
        s.setDepartment("CSE");
        s.setBatchYear(2026);
        s.setRole(AppUser.Role.STUDENT);
        s.setPasswordHash(encoder.encode(roll));
        s.setMustChangePassword(false);
        users.save(s);
    }
}
