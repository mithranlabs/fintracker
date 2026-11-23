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
 * Unit tests for universal statement parser endpoints in UploadController.
 */
@ExtendWith(MockitoExtension.class)
public class UniversalUploadIntegrationTest {

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

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
    }

    @Test
    void testUniversalParseEndpoint_withValidFile() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalStatementParseResult parseResult = new UniversalStatementParseResult();
        parseResult.setDetectionMethod(UniversalStatementParseResult.DetectionMethod.HEURISTIC);
        parseResult.setOverallConfidence(0.9);

        UniversalStatementTransaction txn = new UniversalStatementTransaction();
        txn.setTransactionDate(LocalDate.of(2026, 1, 1));
        txn.setAmount(new BigDecimal("1000.00"));
        txn.setDirection(UniversalStatementTransaction.Direction.DEBIT);
        txn.setNarration("Test Transaction");
        txn.setConfidenceScore(0.9);
        parseResult.addTransaction(txn);

        when(universalParser.parseText(anyString())).thenReturn(parseResult);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Date Amount Description\n01/01/2026 1000.00 Test Transaction".getBytes()
        );

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(file, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(UniversalParseResponseDTO.class);

        UniversalParseResponseDTO result = (UniversalParseResponseDTO) response.getBody();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalTransactions()).isEqualTo(1);
        assertThat(result.getTransactions()).hasSize(1);
        assertThat(result.getHeuristicCount()).isEqualTo(1);
    }

    @Test
    void testUniversalParseEndpoint_requiresAuthentication() {
        // Given
        when(session.getAttribute("userId")).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test".getBytes());

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(file, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testUniversalParseEndpoint_withEmptyResult() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalStatementParseResult emptyResult = new UniversalStatementParseResult();
        when(universalParser.parseText(anyString())).thenReturn(emptyResult);

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "empty".getBytes());

        // When
        ResponseEntity<?> response = uploadController.uploadUniversal(file, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UniversalParseResponseDTO result = (UniversalParseResponseDTO) response.getBody();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("No transactions found");
    }

    @Test
    void testSaveEndpoint_withValidTransactions() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(categoryRepository.findByName(anyString())).thenReturn(null);
        when(merchantRuleRepository.findByKeyword(anyString())).thenReturn(Optional.empty());

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        List<UniversalTransactionDTO> transactions = new ArrayList<>();

        UniversalTransactionDTO txn = new UniversalTransactionDTO();
        txn.setDate("01 Jan 2026");
        txn.setAmount(1000.00);
        txn.setDirection("DEBIT");
        txn.setNarration("Test Purchase");
        transactions.add(txn);

        request.setTransactions(transactions);
        request.setFileName("test.txt");
        request.setReplaceExisting(false);

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).asString().contains("Successfully saved");
        verify(transactionRepository, times(1)).saveAll(anyList());
        verify(uploadRepository, times(1)).save(any());
    }

    @Test
    void testSaveEndpoint_rejectsUnknownDirection() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        List<UniversalTransactionDTO> transactions = new ArrayList<>();

        UniversalTransactionDTO txn = new UniversalTransactionDTO();
        txn.setDate("01 Jan 2026");
        txn.setAmount(1000.00);
        txn.setDirection("UNKNOWN");
        txn.setNarration("Test");
        transactions.add(txn);

        request.setTransactions(transactions);
        request.setFileName("test.txt");

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).asString().contains("UNKNOWN direction");
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void testSaveEndpoint_rejectsInvalidDirection() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        List<UniversalTransactionDTO> transactions = new ArrayList<>();

        UniversalTransactionDTO txn = new UniversalTransactionDTO();
        txn.setDate("01 Jan 2026");
        txn.setAmount(1000.00);
        txn.setDirection("INVALID");
        txn.setNarration("Test");
        transactions.add(txn);

        request.setTransactions(transactions);
        request.setFileName("test.txt");

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).asString().contains("Invalid direction");
    }

    @Test
    void testSaveEndpoint_requiresAuthentication() {
        // Given
        when(session.getAttribute("userId")).thenReturn(null);

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        request.setTransactions(new ArrayList<>());

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testSaveEndpoint_rejectsEmptyTransactionList() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        UniversalSaveRequestDTO request = new UniversalSaveRequestDTO();
        request.setTransactions(new ArrayList<>());

        // When
        ResponseEntity<?> response = uploadController.saveUniversalTransactions(request, session);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).asString().contains("No transactions to save");
    }
}
