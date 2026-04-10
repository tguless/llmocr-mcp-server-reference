package com.llmocr.mcp.invoice.integration;

/**
 * Must match PaperIQ registration defaults ({@code NewUserSetupService} / system template "Invoice Processing").
 * Passed as {@code promptTemplateName} to {@code /service/s3-upload/upload} for server-side bucket resolution.
 */
public final class InvoicePaperIqPromptTemplateNames {

    public static final String INVOICE_PROCESSING = "Invoice Processing";

    private InvoicePaperIqPromptTemplateNames() {
    }
}
