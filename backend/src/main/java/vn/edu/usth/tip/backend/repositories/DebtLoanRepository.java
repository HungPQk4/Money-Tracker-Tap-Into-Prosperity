package vn.edu.usth.tip.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.usth.tip.backend.models.DebtLoan;

import java.util.List;

@Repository
public interface DebtLoanRepository extends JpaRepository<DebtLoan, Long> {
    List<DebtLoan> findByUserId(Long userId);
}
