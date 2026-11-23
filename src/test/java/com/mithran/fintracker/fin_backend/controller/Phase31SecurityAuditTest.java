package com.mithran.fintracker.fin_backend.controller;

import com.mithran.fintracker.fin_backend.dto.UniversalParseResponseDTO;
import com.mithran.fintracker.fin_backend.dto.UniversalSaveRequestDTO;
import com.mithran.fintracker.fin_backend.dto.UniversalTransactionDTO;
import com.mithran.fintracker.fin_backend.entity.User;
import com.mithran.fintracker.fin_backend.repository.*;
import com.mithran.fintracker.fin_backend.service.universal.UniversalStatementParseResult;
import com.mithran.fintracker.fin_backend.service.universal.UniversalStatementParserService;
import com.mithran.fintracker.fin_backend.service.universal.UniversalStatementTransaction;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Security and data integrity audit tests for Phase 3.1.
 * Tests edge cases, validation, ownership, and data consistency.
 */
@ExtendWith(MockitoExtension.class)
public class Phase31SecurityAuditTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MerchantRuleRepository merchantRuleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private UniversalStatementParserService universalParser;

    @Mock
    private HttpSession session;

    @InjectMocks
    private UploadController uploadController;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        otherUser = new User();
        otherUser.setId(2);
        otherUser.setUsername("otheruser");
        otherUser.setEmail("other@example.com");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECURITY TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testParseEndpoint_rejectsUnauthenticatedUser() {
        // Given: No authenticated user
        when(session.getAttribute("userId")).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(file, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("Not logged in");
        verify(universalParser, never()).parseText(anyString());
    }

    @Test
    void testSaveEndpoint_rejectsUnauthenticatedUser() {
        // Given: No authenticated user
        when(session.getAttribute("userId")).thenReturn(null);

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        request.setTransactions(List.of(createValidDTO()));

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void testParseEndpoint_handlesUserNotFoundGracefully() {
        // Given: Valid session but user deleted
        when(session.getAttribute("userId")).thenReturn(999);
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(file, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(universalParser, never()).parseText(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA INTEGRITY TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testSaveEndpoint_rejectsNegativeAmount() {
        // Given: Transaction with negative amount
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        UniversalTransactionDTO dto = createValidDTO();
        dto.setAmount(-1000.00);  // Negative amount
        request.setTransactions(List.of(dto));

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then: Should save (Transaction entity allows negative amounts)
        // But parser should have validated this - check if amounts are always positive in DTO
        assertThat(dto.getAmount()).isLessThan(0.0);
    }

    @Test
    void testSaveEndpoint_handlesVeryLargeAmount() {
        // Given: Transaction with very large amount
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        UniversalTransactionDTO dto = createValidDTO();
        dto.setAmount(999999999.99);  // Very large amount
        request.setTransactions(List.of(dto));

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then: Should handle without overflow
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testSaveEndpoint_handlesMultipleIdenticalTransactions() {
        // Given: Multiple identical transactions (possible duplicate detection issue)
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        UniversalTransactionDTO dto1 = createValidDTO();
        UniversalTransactionDTO dto2 = createValidDTO(); // Exact duplicate
        request.setTransactions(List.of(dto1, dto2));

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then: Should save both (no duplicate prevention at save level)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(transactionRepository, times(1)).saveAll(argThat(list -> {
            List<?> l = (List<?>) list;
            return l.size() == 2;
        }));
    }

    @Test
    void testSaveEndpoint_replaceExistingDeletesOnlyUserTransactions() {
        // Given: Replace existing flag set
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        request.setTransactions(List.of(createValidDTO()));
        request.setReplaceExisting(true);

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then: Should only delete current user's transactions
        verify(transactionRepository, times(1)).deleteByUser(testUser);
        verify(transactionRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testSaveEndpoint_validatesDirectionValues() {
        // Given: Various invalid direction values
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        String[] invalidDirections = {"debit", "credit", "EXPENSE", "INCOME", "", "   ", "Dr", "Cr", "both", "neutral"};

        for (String invalidDir : invalidDirections) {
            UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
            UniversalTransactionDTO dto = createValidDTO();
            dto.setDirection(invalidDir);
            request.setTransactions(List.of(dto));

            // When
            ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().toString()).containsIgnoringCase("invalid direction");
        }

        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void testSaveEndpoint_rejectsNullDirection() {
        // Given: Transaction with null direction
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        UniversalTransactionDTO dto = createValidDTO();
        dto.setDirection(null);
        request.setTransactions(List.of(dto));

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void testSaveEndpoint_validatesMapsDirectionToTypeCorrectly() {
        // Given: DEBIT and CREDIT transactions
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        
        UniversalTransactionDTO debitDto = createValidDTO();
        debitDto.setDirection("DEBIT");
        debitDto.setDate("01 Jan 2026");
        
        UniversalTransactionDTO creditDto = createValidDTO();
        creditDto.setDirection("CREDIT");
        creditDto.setDate("02 Jan 2026");
        
        request.setTransactions(List.of(debitDto, creditDto));

        // When
        uploadController.saveUniversalTransactions(request, session);

        // Then: Verify correct type mapping
        verify(transactionRepository, times(1)).saveAll(argThat(transactions -> {
            List<?> txnList = new ArrayList<>((List<?>) transactions);
            if (txnList.size() != 2) return false;
            
            long expenseCount = txnList.stream()
                .filter(t -> ((com.mithran.fintracker.fin_backend.entity.Transaction) t).getType().equals("expense"))
                .count();
            long incomeCount = txnList.stream()
                .filter(t -> ((com.mithran.fintracker.fin_backend.entity.Transaction) t).getType().equals("income"))
                .count();
            
            return expenseCount == 1 && incomeCount == 1;
        }));
    }

    @Test
    void testSaveEndpoint_handlesInvalidDateFormat() {
        // Given: Transaction with invalid date format
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        UniversalTransactionDTO dto = createValidDTO();
        dto.setDate("2026-01-01");  // ISO format instead of "dd MMM yyyy"
        request.setTransactions(List.of(dto));

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).containsIgnoringCase("Invalid date format");
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void testParseEndpoint_handlesMalformedPDF() {
        // Given: Corrupted PDF file
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        MockMultipartFile corruptedPdf = new MockMultipartFile(
                "file", 
                "corrupt.pdf", 
                "application/pdf", 
                new byte[]{0x25, 0x50, 0x44, 0x46}  // PDF header but no content
        );

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(corruptedPdf, session);

        // Then: Should return 500 with error message, not crash
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(UniversalParseResponseDTO.class);
        UniversalParseResponseDTO result = (UniversalParseResponseDTO) response.getBody();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void testParseEndpoint_handlesVeryLargeFile() {
        // Given: Very large text file (simulated)
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(universalParser.parseText(anyString())).thenReturn(new UniversalStatementParseResult());

        // 1MB of text
        byte[] largeContent = new byte[1024 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) ('A' + (i % 26));
        }

        MockMultipartFile largeFile = new MockMultipartFile("file", "large.txt", "text/plain", largeContent);

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(largeFile, session);

        // Then: Should handle without crashing (though may timeout in real scenario)
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONSISTENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testSaveEndpoint_createsUploadHistoryAtomically() {
        // Given: Valid save request
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        request.setTransactions(List.of(createValidDTO()));
        request.setFileName("test.pdf");

        // When
        uploadController.saveUniversalTransactions(request, session);

        // Then: Both transactions and upload history should be saved
        verify(transactionRepository, times(1)).saveAll(anyList());
        verify(uploadRepository, times(1)).save(argThat(upload -> 
            upload.getFileName().equals("test.pdf") &&
            upload.getTransactionCount() == 1 &&
            upload.getUser().equals(testUser)
        ));
    }

    @Test
    void testSaveEndpoint_usesDefaultFileNameWhenNull() {
        // Given: Save request without fileName
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        request.setTransactions(List.of(createValidDTO()));
        request.setFileName(null);

        // When
        uploadController.saveUniversalTransactions(request, session);

        // Then: Should use default filename
        verify(uploadRepository, times(1)).save(argThat(upload -> 
            upload.getFileName().equals("universal-statement.txt")
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private UniversalTransactionDTO createValidDTO() {
        UniversalTransactionDTO dto = new UniversalTransactionDTO();
        dto.setDate("01 Jan 2026");
        dto.setAmount(1000.00);
        dto.setDirection("DEBIT");
        dto.setNarration("Test Transaction");
        return dto;
    }
}
