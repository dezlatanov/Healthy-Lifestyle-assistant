package bg.pu.hla.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import bg.pu.hla.domain.DailyLog;
import bg.pu.hla.domain.UserProfile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {
    List<DailyLog> findByUserOrderByLogDateDesc(UserProfile user);
    Optional<DailyLog> findFirstByUserOrderByLogDateDesc(UserProfile user);
    Optional<DailyLog> findByUserAndLogDate(UserProfile user, LocalDate logDate);
}
