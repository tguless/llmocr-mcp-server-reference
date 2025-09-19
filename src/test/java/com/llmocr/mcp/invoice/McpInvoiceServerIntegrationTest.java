package com.llmocr.mcp.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmocr.mcp.invoice.domain.Invoice;
import com.llmocr.mcp.invoice.repository.InvoiceRepository;
import com.llmocr.mcp.invoice.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MCP Invoice Server
 * 
 * Tests the complete MCP server functionality including:
 * - Multi-tenant security
 * - MCP tool execution
 * - Database operations
 * - Authentication and authorization
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class McpInvoiceServerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("mcp_invoice_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @LocalServerPort
    private int port;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/mcp-invoice";
        
        // Clean up test data
        invoiceRepository.deleteAll();
    }

    @Test
    void testMcpServerHealth() throws Exception {
        mockMvc.perform(get(baseUrl + "/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testProcessInvoiceMcpTool() throws Exception {
        // Prepare MCP tool call request
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("name", "process-invoice");
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("invoiceNumber", "TEST-001");
        arguments.put("vendorName", "Test Vendor");
        arguments.put("invoiceDate", "2024-01-15");
        arguments.put("totalAmount", "1500.00");
        
        toolCall.put("arguments", arguments);
        
        // Add MCP metadata for authentication
        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", "test-tenant");
        meta.put("userId", "test-user");
        meta.put("userToken", "test-jwt-token");
        
        toolCall.put("_meta", meta);

        // Execute MCP tool call
        mockMvc.perform(post(baseUrl + "/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toolCall)))
                .andExpect(status().isOk());

        // Verify invoice was created in database
        assertTrue(invoiceRepository.existsByTenantIdAndInvoiceNumber("test-tenant", "TEST-001"));
    }

    @Test
    void testCheckInvoiceExistsMcpTool() throws Exception {
        // Create test invoice first
        Invoice testInvoice = Invoice.builder()
                .tenantId("test-tenant")
                .invoiceNumber("TEST-002")
                .vendorName("Test Vendor 2")
                .invoiceDate(LocalDate.now())
                .totalAmount(new BigDecimal("2000.00"))
                .build();
        
        invoiceRepository.save(testInvoice);

        // Prepare MCP tool call request
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("name", "check-invoice-exists");
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("invoiceNumber", "TEST-002");
        
        toolCall.put("arguments", arguments);
        
        // Add MCP metadata
        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", "test-tenant");
        meta.put("userId", "test-user");
        
        toolCall.put("_meta", meta);

        // Execute MCP tool call
        mockMvc.perform(post(baseUrl + "/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toolCall)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void testMultiTenantIsolation() throws Exception {
        // Create invoices for different tenants
        Invoice tenant1Invoice = Invoice.builder()
                .tenantId("tenant-1")
                .invoiceNumber("INV-001")
                .vendorName("Vendor A")
                .invoiceDate(LocalDate.now())
                .totalAmount(new BigDecimal("1000.00"))
                .build();

        Invoice tenant2Invoice = Invoice.builder()
                .tenantId("tenant-2")
                .invoiceNumber("INV-001") // Same invoice number, different tenant
                .vendorName("Vendor B")
                .invoiceDate(LocalDate.now())
                .totalAmount(new BigDecimal("2000.00"))
                .build();

        invoiceRepository.save(tenant1Invoice);
        invoiceRepository.save(tenant2Invoice);

        // Test that tenant-1 can only see their invoice
        Map<String, Object> toolCall1 = createCheckInvoiceToolCall("INV-001", "tenant-1");
        
        mockMvc.perform(post(baseUrl + "/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toolCall1)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        // Test that tenant-2 can also see their invoice (same number, different tenant)
        Map<String, Object> toolCall2 = createCheckInvoiceToolCall("INV-001", "tenant-2");
        
        mockMvc.perform(post(baseUrl + "/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toolCall2)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        // Verify database isolation
        assertEquals(1, invoiceRepository.findByTenantId("tenant-1").size());
        assertEquals(1, invoiceRepository.findByTenantId("tenant-2").size());
    }

    @Test
    void testInvoiceResourceAccess() throws Exception {
        // Create test invoice
        Invoice testInvoice = Invoice.builder()
                .tenantId("test-tenant")
                .invoiceNumber("RES-001")
                .vendorName("Resource Test Vendor")
                .invoiceDate(LocalDate.now())
                .totalAmount(new BigDecimal("3000.00"))
                .build();
        
        Invoice savedInvoice = invoiceRepository.save(testInvoice);

        // Test resource access
        mockMvc.perform(get(baseUrl + "/mcp/resources/invoice/" + savedInvoice.getId())
                .header("X-Tenant-ID", "test-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("RES-001"))
                .andExpect(jsonPath("$.vendorName").value("Resource Test Vendor"));
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        // Create invoice for tenant-1
        Invoice testInvoice = Invoice.builder()
                .tenantId("tenant-1")
                .invoiceNumber("SECURE-001")
                .vendorName("Secure Vendor")
                .invoiceDate(LocalDate.now())
                .totalAmount(new BigDecimal("5000.00"))
                .build();
        
        invoiceRepository.save(testInvoice);

        // Try to access with different tenant context
        Map<String, Object> toolCall = createCheckInvoiceToolCall("SECURE-001", "tenant-2");
        
        mockMvc.perform(post(baseUrl + "/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toolCall)))
                .andExpect(status().isOk())
                .andExpect(content().string("false")); // Should not find invoice from different tenant
    }

    private Map<String, Object> createCheckInvoiceToolCall(String invoiceNumber, String tenantId) {
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("name", "check-invoice-exists");
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("invoiceNumber", invoiceNumber);
        toolCall.put("arguments", arguments);
        
        Map<String, Object> meta = new HashMap<>();
        meta.put("tenantId", tenantId);
        meta.put("userId", "test-user");
        toolCall.put("_meta", meta);
        
        return toolCall;
    }
}
