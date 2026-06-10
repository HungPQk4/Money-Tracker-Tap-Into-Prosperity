package vn.edu.usth.tip.backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.usth.tip.backend.dto.transaction.SyncRequest;
import vn.edu.usth.tip.backend.dto.transaction.SyncResponse;
import vn.edu.usth.tip.backend.dto.transaction.SyncTransactionRequest;
import vn.edu.usth.tip.backend.models.Account;
import vn.edu.usth.tip.backend.models.Category;
import vn.edu.usth.tip.backend.models.Transaction;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.TransactionType;
import vn.edu.usth.tip.backend.repositories.*;
import vn.edu.usth.tip.backend.utils.SecurityUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Guards syncTransactions() LWW logic so the extract-method refactor does not change behaviour.
 * Tests the two critical paths: new insert (PATH B) and LWW client-wins edit (PATH A).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceSyncTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Account testAccount;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());

        testAccount = new Account();
        testAccount.setId(UUID.randomUUID());
        testAccount.setBalance(BigDecimal.ZERO);

        testCategory = new Category();
        testCategory.setId(UUID.randomUUID());
        testCategory.setName("Food");

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(accountRepository.findById(testAccount.getId())).thenReturn(Optional.of(testAccount));
        when(categoryRepository.findById(testCategory.getId())).thenReturn(Optional.of(testCategory));

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        savedTx.setUser(testUser);
        savedTx.setAccount(testAccount);
        savedTx.setCategory(testCategory);
        savedTx.setAmount(BigDecimal.valueOf(50000));
        savedTx.setType(TransactionType.expense);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        // Sau refactor "số dư = Σ giao dịch", sync chỉ ghi account khi số dư đổi → stub này có thể không dùng.
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** PATH B: new transaction with client-supplied UUID — server must preserve the UUID. */
    @Test
    void syncTransactions_pathB_newTransactionWithUUID_savesWithClientUUID() {
        UUID clientUUID = UUID.randomUUID();

        SyncTransactionRequest item = new SyncTransactionRequest();
        item.setTransactionId(clientUUID);
        item.setAccountId(testAccount.getId());
        item.setCategoryId(testCategory.getId());
        item.setAmount(BigDecimal.valueOf(50000));
        item.setType(TransactionType.expense);
        item.setTransactionDate(LocalDate.now());

        // No existing record for this UUID
        when(transactionRepository.findByIdAndUser_Id(clientUUID, testUser.getId()))
                .thenReturn(Optional.empty());

        SyncRequest req = new SyncRequest();
        req.setUserId(testUser.getId());
        req.setTransactions(List.of(item));

        SyncResponse response = transactionService.syncTransactions(req);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(clientUUID);
        assertThat(response.getSavedCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(0);
    }

    /** PATH A, client wins: client's updatedAt is newer → server must apply the edit. */
    @Test
    void syncTransactions_pathA_clientWinsLWW_appliesClientEdits() {
        UUID txId = UUID.randomUUID();

        Transaction existing = new Transaction();
        existing.setId(txId);
        existing.setUser(testUser);
        existing.setAccount(testAccount);
        existing.setCategory(testCategory);
        existing.setAmount(BigDecimal.valueOf(30000));
        existing.setType(TransactionType.expense);
        existing.setUpdatedAt(OffsetDateTime.now().minusHours(2));

        when(transactionRepository.findByIdAndUser_Id(txId, testUser.getId()))
                .thenReturn(Optional.of(existing));

        SyncTransactionRequest item = new SyncTransactionRequest();
        item.setTransactionId(txId);
        item.setAccountId(testAccount.getId());
        item.setCategoryId(testCategory.getId());
        item.setAmount(BigDecimal.valueOf(75000));
        item.setType(TransactionType.expense);
        item.setTransactionDate(LocalDate.now());
        item.setClientUpdatedAt(OffsetDateTime.now()); // newer than existing.updatedAt

        SyncRequest req = new SyncRequest();
        req.setUserId(testUser.getId());
        req.setTransactions(List.of(item));

        SyncResponse response = transactionService.syncTransactions(req);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(75000));
        assertThat(response.getSavedCount()).isEqualTo(1);
    }
}
