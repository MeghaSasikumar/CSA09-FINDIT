package com.college.findit.repo;

import com.college.findit.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByRollNo(String rollNo);
    boolean existsByRollNo(String rollNo);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(AppUser.Role role);
    List<AppUser> findByRole(AppUser.Role role);
    List<AppUser> findByBatchYearAndRole(Integer batchYear, AppUser.Role role);
    List<AppUser> findAllByOrderByRoleAscRollNoAsc();
}
