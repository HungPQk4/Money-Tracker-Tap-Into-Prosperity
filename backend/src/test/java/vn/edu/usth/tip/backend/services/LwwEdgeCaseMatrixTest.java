package vn.edu.usth.tip.backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
 * Evidence companion to Table "Last-Write-Wins edge cases" ([tab:lww]).
 * One test per row: each asserts the decision the service actually makes, so the
 * six edge cases are demonstrably tested rather than merely described.
 *
 * Mirrors TransactionServiceSyncTest (pure Mockito, no Spring context).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LwwEdgeCaseMatrixTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks private TransactionService service;

    private User user;
    private Account account;
    private Category category;

    private static final BigDecimal SERVER_AMOUNT = BigDecimal.valueOf(30000);
    private static final BigDecimal CLIENT_AMOUNT = BigDecimal.valueOf(75000);

    @BeforeEach
    void setUp() {
        user = new User();         user.setId(UUID.randomUUID());
        account = new Account();   account.setId(UUID.randomUUID()); account.setBalance(BigDecimal.ZERO);
        category = new Category(); category.setId(UUID.randomUUID()); category.setName("Food");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Row 1: client timestamp newer → CLIENT wins ──────────────────────────────
    @Test
    @DisplayName("client ts newer -> client wins (edit applied)")
    void clientNewer_clientWins() {
        UUID id = existing(OffsetDateTime.now().minusHours(2));
        SyncResponse r = sync(editItem(id, OffsetDateTime.now()));
        assertThat(r.getSavedCount()).isEqualTo(1);
        assertThat(r.getSavedTransactions().get(0).getAmount()).isEqualByComparingTo(CLIENT_AMOUNT);
    }

    // ── Row 2: server timestamp newer → SERVER wins ──────────────────────────────
    @Test
    @DisplayName("server ts newer -> server wins (client overwrite rejected)")
    void serverNewer_serverWins() {
        UUID id = existing(OffsetDateTime.now());                 // server fresh
        SyncResponse r = sync(editItem(id, OffsetDateTime.now().minusHours(2)));
        assertThat(r.getSavedCount()).isEqualTo(0);
        assertThat(r.getSavedTransactions().get(0).getAmount()).isEqualByComparingTo(SERVER_AMOUNT);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ── Row 3: client timestamp null (legacy) → SERVER wins ──────────────────────
    @Test
    @DisplayName("client ts null -> server wins (no legacy overwrite)")
    void clientNull_serverWins() {
        UUID id = existing(OffsetDateTime.now().minusHours(2));
        SyncResponse r = sync(editItem(id, null));               // legacy client, no timestamp
        assertThat(r.getSavedCount()).isEqualTo(0);
        assertThat(r.getSavedTransactions().get(0).getAmount()).isEqualByComparingTo(SERVER_AMOUNT);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ── Row 4: server timestamp null (old row) → CLIENT wins ─────────────────────
    @Test
    @DisplayName("server ts null -> client wins (old unstamped row)")
    void serverNull_clientWins() {
        UUID id = existing(null);                                 // old row never stamped
        SyncResponse r = sync(editItem(id, OffsetDateTime.now()));
        assertThat(r.getSavedCount()).isEqualTo(1);
        assertThat(r.getSavedTransactions().get(0).getAmount()).isEqualByComparingTo(CLIENT_AMOUNT);
    }

    // ── Row 5: deleted but client resends → RE-ACKNOWLEDGE (no resurrection) ──────
    @Test
    @DisplayName("already-deleted resend -> re-acknowledged, no save")
    void deletedResend_reAcknowledged() {
        UUID id = UUID.randomUUID();
        Transaction tomb = baseTx(id);
        tomb.setDeletedAt(OffsetDateTime.now().minusDays(1));     // already a tombstone
        when(transactionRepository.findByIdAndUser_Id(id, user.getId())).thenReturn(Optional.of(tomb));

        SyncTransactionRequest del = new SyncTransactionRequest();
        del.setTransactionId(id);
        del.setDeleted(true);

        SyncResponse r = sync(del);
        assertThat(r.getSavedTransactions()).hasSize(1);
        assertThat(r.getSavedTransactions().get(0).isDeleted()).isTrue();
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ── Row 6: duplicate new (legacy, id null) → RETURN EXISTING (no double-insert) ─
    @Test
    @DisplayName("legacy duplicate (id null) -> returns existing, skipped")
    void legacyDuplicate_returnsExisting() {
        Transaction dup = baseTx(UUID.randomUUID());
        when(transactionRepository.findDuplicate(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(dup));

        SyncTransactionRequest item = new SyncTransactionRequest();   // transactionId == null
        item.setAccountId(account.getId());
        item.setCategoryId(category.getId());
        item.setAmount(CLIENT_AMOUNT);
        item.setType(TransactionType.expense);
        item.setTransactionDate(LocalDate.now());

        SyncResponse r = sync(item);
        assertThat(r.getSavedCount()).isEqualTo(0);
        assertThat(r.getSkippedCount()).isEqualTo(1);
        assertThat(r.getSavedTransactions().get(0).getId()).isEqualTo(dup.getId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────────
    private UUID existing(OffsetDateTime serverUpdatedAt) {
        UUID id = UUID.randomUUID();
        Transaction tx = baseTx(id);
        tx.setUpdatedAt(serverUpdatedAt);
        when(transactionRepository.findByIdAndUser_Id(id, user.getId())).thenReturn(Optional.of(tx));
        return id;
    }

    private Transaction baseTx(UUID id) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setUser(user);
        tx.setAccount(account);
        tx.setCategory(category);
        tx.setAmount(SERVER_AMOUNT);
        tx.setType(TransactionType.expense);
        return tx;
    }

    private SyncTransactionRequest editItem(UUID id, OffsetDateTime clientTs) {
        SyncTransactionRequest item = new SyncTransactionRequest();
        item.setTransactionId(id);
        item.setAccountId(account.getId());
        item.setCategoryId(category.getId());
        item.setAmount(CLIENT_AMOUNT);
        item.setType(TransactionType.expense);
        item.setTransactionDate(LocalDate.now());
        item.setClientUpdatedAt(clientTs);
        return item;
    }

    private SyncResponse sync(SyncTransactionRequest item) {
        SyncRequest req = new SyncRequest();
        req.setUserId(user.getId());
        req.setTransactions(List.of(item));
        return service.syncTransactions(req);
    }
}
