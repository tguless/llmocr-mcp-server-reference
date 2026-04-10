// Environment configuration for external app URLs
export const config = {
  // LLM-OCR Platform URL
  // Development: http://localhost:3000/llmocr/
  // Production: https://eyesense.ai/llmocr/
  llmocrAppUrl: process.env.REACT_APP_LLMOCR_APP_URL || 
    (process.env.NODE_ENV === 'production' 
      ? 'https://eyesense.ai/llmocr/' 
      : 'http://localhost:3000/llmocr/'),
};

