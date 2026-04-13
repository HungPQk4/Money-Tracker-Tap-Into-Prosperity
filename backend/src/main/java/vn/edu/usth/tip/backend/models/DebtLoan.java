package vn.edu.usth.tip.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "debts_loans")
@Data
@NoArgsConstructor
public class DebtLoan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String type; // 'DEBT' or 'LOAN'

    @Column(name = "counterparty_name", nullable = false)
    private String counterpartyName;

    @Column(nullable = false)
    private Double amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status; // 'PENDING', 'COMPLETED'
}
