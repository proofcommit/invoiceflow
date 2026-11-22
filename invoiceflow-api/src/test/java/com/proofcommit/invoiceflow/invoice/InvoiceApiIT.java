package com.proofcommit.invoiceflow.invoice;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofcommit.invoiceflow.IntegrationTestSupport;
import com.proofcommit.invoiceflow.outbox.OutboxRepository;
import com.proofcommit.invoiceflow.security.ApiKeyFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class InvoiceApiIT extends IntegrationTestSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OutboxRepository outbox;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        payments.deleteAll();
        invoices.deleteAll();
    }

    @Test
    void createsIssuesAndPaysAnInvoice() throws Exception {
        UUID invoiceId = createInvoice();

        mvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId).header(ApiKeyFilter.HEADER, "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ISSUED")));

        mvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                .header(ApiKeyFilter.HEADER, "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"partial-1\",\"amount\":40.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invoiceStatus", is("PARTIALLY_PAID")))
            .andExpect(jsonPath("$.outstanding", is(110.00)));

        mvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                .header(ApiKeyFilter.HEADER, "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"final-1\",\"amount\":110.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invoiceStatus", is("PAID")))
            .andExpect(jsonPath("$.outstanding", is(0.00)));

        mvc.perform(get("/api/v1/invoices/{id}", invoiceId).header(ApiKeyFilter.HEADER, "test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PAID")))
            .andExpect(jsonPath("$.paidAmount", is(150.00)));
    }

    @Test
    void duplicatePaymentReferenceIsIdempotent() throws Exception {
        UUID invoiceId = createInvoice();
        issue(invoiceId);
        String body = "{\"paymentReference\":\"same-reference\",\"amount\":150.00}";

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                    .header(ApiKeyFilter.HEADER, "test-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }

        org.junit.jupiter.api.Assertions.assertEquals(1, payments.countByInvoiceId(invoiceId));
        org.junit.jupiter.api.Assertions.assertEquals(2, outbox.count());
    }

    @Test
    void rejectsOverpaymentAndPaymentBeforeIssue() throws Exception {
        UUID draftId = createInvoice();
        mvc.perform(post("/api/v1/invoices/{id}/payments", draftId)
                .header(ApiKeyFilter.HEADER, "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"early\",\"amount\":10.00}"))
            .andExpect(status().isConflict());

        issue(draftId);
        mvc.perform(post("/api/v1/invoices/{id}/payments", draftId)
                .header(ApiKeyFilter.HEADER, "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentReference\":\"too-much\",\"amount\":150.01}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invoice rule violated")));
    }

    @Test
    void validatesRequestsAndRequiresAnApiKey() throws Exception {
        mvc.perform(post("/api/v1/invoices")
                .header(ApiKeyFilter.HEADER, "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":null,\"customerEmail\":\"not-an-email\",\"currency\":\"dollars\",\"lines\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Request validation failed")));

        mvc.perform(get("/api/v1/invoices/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundForUnknownInvoice() throws Exception {
        mvc.perform(get("/api/v1/invoices/{id}", UUID.randomUUID()).header(ApiKeyFilter.HEADER, "test-key"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title", is("Invoice not found")));
    }

    @Test
    void exposesOpenApiDocumentationWithoutCredentials() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title", is("InvoiceFlow API")));
    }

    private UUID createInvoice() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/invoices")
                .header(ApiKeyFilter.HEADER, "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "7a4ce2c4-5a63-4a76-bf35-e1631c2641a8",
                      "customerEmail": "customer@example.com",
                      "currency": "USD",
                      "dueDate": "2099-12-31",
                      "lines": [
                        {"description": "Assessment", "quantity": 2, "unitPrice": 50.00},
                        {"description": "Verification", "quantity": 1, "unitPrice": 50.00}
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total", is(150.00)))
            .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private void issue(UUID invoiceId) throws Exception {
        mvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId).header(ApiKeyFilter.HEADER, "test-key"))
            .andExpect(status().isOk());
    }
}
