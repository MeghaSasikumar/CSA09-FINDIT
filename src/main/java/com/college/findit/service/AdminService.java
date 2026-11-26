package com.college.findit.service;

import com.college.findit.model.AppUser;
import com.college.findit.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Onboarding of new student batches: CSV bulk import, manual add, graduation, password reset. */
@Service
public class AdminService {

    public record ImportResult(int added, int skipped, List<String> errors) { }

    @Value("${findit.allowed-email-domain}")
    private String allowedDomain;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public String getAllowedDomain() {
        return allowedDomain;
    }

    /** Only college mail ids (for example 192xxxxxx.simats@saveetha.com) are accepted. */
    public boolean isCollegeEmail(String email) {
        return email != null && email.trim().toLowerCase().endsWith("@" + allowedDomain.toLowerCase());
    }

    /** CSV columns: rollNo,name,email,department,batchYear (first row may be a header). */
    @Transactional
    public ImportResult importCsv(MultipartFile file, String adminRoll) throws IOException {
        int added = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.replace("\uFEFF", "").trim();
                if (line.isEmpty()) continue;
                if (lineNo == 1 && line.toLowerCase().startsWith("rollno")) continue;   // header row

                String[] p = line.split(",", -1);
                if (p.length < 5) {
                    errors.add("Line " + lineNo + ": expected 5 columns (rollNo,name,email,department,batchYear)");
                    continue;
                }
                if (!isCollegeEmail(p[2])) {
                    errors.add("Line " + lineNo + ": email must end with @" + allowedDomain);
                    continue;
                }
                try {
                    boolean ok = addStudent(p[0], p[1], p[2], p[3], Integer.parseInt(p[4].trim()));
                    if (ok) added++; else skipped++;
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNo + ": batchYear must be a number");
                }
            }
        }
        audit.log(adminRoll, "BATCH_IMPORT", "Imported " + added + ", skipped " + skipped + ", errors " + errors.size());
        return new ImportResult(added, skipped, errors);
    }

    /** Returns false if the roll number already exists or is empty. Default password = roll number. */
    @Transactional
    public boolean addStudent(String rollNo, String name, String email, String dept, Integer batch) {
        String roll = rollNo == null ? "" : rollNo.trim().toUpperCase();
        if (roll.isEmpty() || users.existsByRollNo(roll)) return false;
        if (!isCollegeEmail(email) || users.existsByEmailIgnoreCase(email.trim())) return false;

        AppUser u = new AppUser();
        u.setRollNo(roll);
        u.setName(name.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setDepartment(dept.trim());
        u.setBatchYear(batch);
        u.setRole(AppUser.Role.STUDENT);
        u.setPasswordHash(encoder.encode(roll));
        u.setMustChangePassword(true);
        users.save(u);
        return true;
    }

    /** Graduation: deactivate every student of a batch (data is kept, login is blocked). */
    @Transactional
    public int deactivateBatch(Integer batchYear, String adminRoll) {
        List<AppUser> list = users.findByBatchYearAndRole(batchYear, AppUser.Role.STUDENT);
        for (AppUser u : list) u.setActive(false);
        users.saveAll(list);
        audit.log(adminRoll, "BATCH_DEACTIVATED", "Batch " + batchYear + ": " + list.size() + " students marked alumni");
        return list.size();
    }

    @Transactional
    public void resetPassword(Long userId, String adminRoll) {
        AppUser u = users.findById(userId).orElseThrow();
        u.setPasswordHash(encoder.encode(u.getRollNo()));
        u.setMustChangePassword(true);
        users.save(u);
        audit.log(adminRoll, "PASSWORD_RESET", "Reset password of " + u.getRollNo());
    }
}
