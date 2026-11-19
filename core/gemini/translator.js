/**
 * Smart Voice Keyboard - Gemini API Translation & AI Voice Commands Engine
 * Powered by Google Gemini 1.5/2.0 API with streaming & real-time bidirectional translation.
 */

class GeminiTranslator {
  constructor(apiKey = process.env.GEMINI_API_KEY) {
    this.apiKey = apiKey;
    this.modelName = 'gemini-1.5-flash';
    this.baseUrl = 'https://generativelanguage.googleapis.com/v1beta/models';
  }

  setApiKey(key) {
    this.apiKey = key;
  }

  /**
   * Translate text between English and Bengali
   * @param {string} text - Source text
   * @param {string} sourceLang - 'en' or 'bn'
   * @param {string} targetLang - 'bn' or 'en'
   * @returns {Promise<string>}
   */
  async translate(text, sourceLang = 'en', targetLang = 'bn') {
    if (!text || !text.trim()) return '';
    if (!this.apiKey) {
      throw new Error('GEMINI_API_KEY_NOT_CONFIGURED');
    }

    const directionDesc = (sourceLang === 'en' && targetLang === 'bn') 
      ? 'English to fluent, authentic Bengali' 
      : 'Bengali to natural, fluent English';

    const systemPrompt = `You are a high-speed professional translation engine specializing in English and Bengali. 
Translate the input from ${directionDesc}.
Rules:
- Output ONLY the translated text without explanations, greetings, quotes, or metadata.
- Maintain original formatting, punctuation, numbers, and capitalization.
- Ensure natural phrasing and context accuracy.`;

    const requestBody = {
      contents: [
        {
          role: 'user',
          parts: [{ text: `${systemPrompt}\n\nText to translate:\n${text}` }]
        }
      ],
      generationConfig: {
        temperature: 0.2,
        maxOutputTokens: 1024
      }
    };

    const url = `${this.baseUrl}/${this.modelName}:generateContent?key=${this.apiKey}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`Gemini API error [${response.status}]: ${err}`);
    }

    const data = await response.json();
    const candidate = data.candidates?.[0]?.content?.parts?.[0]?.text;
    return candidate ? candidate.trim() : '';
  }

  /**
   * Execute natural language voice command
   * @param {string} rawVoiceInput
   * @returns {Promise<{action: string, result: string, original: string}>}
   */
  async executeVoiceCommand(rawVoiceInput) {
    if (!rawVoiceInput || !rawVoiceInput.trim()) {
      return { action: 'none', result: '', original: rawVoiceInput };
    }
    if (!this.apiKey) {
      throw new Error('GEMINI_API_KEY_NOT_CONFIGURED');
    }

    const systemInstruction = `You are a voice keyboard assistant. Analyze the user's voice input.
The user might give a direct dictation or a voice command (e.g., 'translate to Bengali: Hello how are you', 'make formal: I want job', 'fix grammar: he go to school yesterday', 'summarize: ...').
Return JSON format ONLY:
{
  "action": "translate_bn" | "translate_en" | "fix_grammar" | "make_formal" | "summarize" | "direct_dictation",
  "result": "<the executed or processed text>",
  "original": "<cleaned original text>"
}`;

    const requestBody = {
      contents: [
        {
          role: 'user',
          parts: [{ text: `${systemInstruction}\n\nUser Voice Input:\n${rawVoiceInput}` }]
        }
      ],
      generationConfig: {
        responseMimeType: "application/json",
        temperature: 0.1
      }
    };

    const url = `${this.baseUrl}/${this.modelName}:generateContent?key=${this.apiKey}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`Gemini Voice Command Error [${response.status}]: ${err}`);
    }

    const data = await response.json();
    const textOutput = data.candidates?.[0]?.content?.parts?.[0]?.text;
    try {
      return JSON.parse(textOutput);
    } catch (e) {
      return { action: 'direct_dictation', result: textOutput || rawVoiceInput, original: rawVoiceInput };
    }
  }

  /**
   * Health check / validation of API key
   */
  async validateKey() {
    try {
      const test = await this.translate('Hello', 'en', 'bn');
      return { valid: true, sample: test };
    } catch (err) {
      return { valid: false, error: err.message };
    }
  }
}

module.exports = { GeminiTranslator };
