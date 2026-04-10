#!/bin/sh

# Wait for MinIO to be ready
sleep 5

# Configure mc client
mc alias set myminio http://minio:9000 minioadmin minioadmin

# Create the invoices bucket
echo "Creating 'invoices' bucket..."
mc mb myminio/invoices --ignore-existing

# Create the shared invoices bucket (matches default S3 bucket name)
echo "Creating 'llm-ocr-invoices' bucket..."
mc mb myminio/llm-ocr-invoices --ignore-existing

# Enable versioning
echo "Enabling versioning on invoices buckets..."
mc version enable myminio/invoices 2>/dev/null || true
mc version enable myminio/llm-ocr-invoices 2>/dev/null || true

echo "MinIO initialization complete"
echo "Buckets created: invoices, llm-ocr-invoices"
