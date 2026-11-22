/**
 * Smart Voice Keyboard - Gemini API Translation & AI Voice Commands Engine
 * Powered by Google Gemini 1.5/2.0 API with streaming & real-time bidirectional translation.
 */

class GeminiTranslator {
  constructor(apiKey = process.env.GEMINI_API_KEY) {
    this.apiKey = apiKey;
    this.modelName = 'gemini-flash-lite-latest';
    this.baseUrl = 'https://generativelanguage.googleapis.com/v1beta/models';
  }

  setApiKey(key) {
    this.apiKey = key;
  }

  /**
   * Resilient request with automatic candidate model and API version fallback
   */
  async requestWithFallback(requestBody) {
    const candidates = [
      { ver: 'v1beta', model: this.modelName || 'gemini-flash-lite-latest' },
      { ver: 'v1beta', model: 'gemini-flash-lite-latest' },
      { ver: 'v1beta', model: 'gemini-3.6-flash' },
      { ver: 'v1beta', model: 'gemini-3.5-flash' },
      { ver: 'v1beta', model: 'gemini-3.5-flash-lite' },
      { ver: 'v1beta', model: 'gemini-3.1-flash-lite' },
      { ver: 'v1', model: 'gemini-flash-lite-latest' }
    ];

    let lastErr = '';
    let lastStatus = 0;

    for (const c of candidates) {
      const url = `https://generativelanguage.googleapis.com/${c.ver}/models/${c.model}:generateContent?key=${this.apiKey}`;
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(requestBody)
        });

        if (response.ok) {
          this.modelName = c.model;
          return await response.json();
        }

        lastStatus = response.status;
        lastErr = await response.text();

        if (response.status !== 404 && response.status !== 400) {
          break; // Stop on auth (401/403) or rate limits (429)
        }
      } catch (err) {
        lastErr = err.message;
      }
    }

    // Dynamic auto-discovery fallback if candidates fail
    try {
      const listRes = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${this.apiKey}`);
      if (listRes.ok) {
        const listData = await listRes.json();
        const liveModels = (listData.models || [])
          .filter(m => (m.supportedGenerationMethods || []).includes('generateContent'))
          .map(m => m.name.replace('models/', ''))
          .filter(m => !m.includes('tts') && !m.includes('image') && !m.includes('embedding'));

        for (const lm of liveModels.slice(0, 5)) {
          const liveUrl = `https://generativelanguage.googleapis.com/v1beta/models/${lm}:generateContent?key=${this.apiKey}`;
          const liveResponse = await fetch(liveUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
          });
          if (liveResponse.ok) {
            this.modelName = lm;
            return await liveResponse.json();
          }
        }
      }
    } catch (_) {}

    if (lastStatus === 404) {
      throw new Error(`গুগল জেমিনি মডেল পাওয়া যায়নি (Error 404)। রুট কজ: গুগল ক্লাউড প্রজেক্টে 'Generative Language API' চালু নেই। সমাধান: aistudio.google.com/apikey এ গিয়ে 'Create API key in NEW project' বেছে নিন।`);
    }
    throw new Error(`Gemini API error [${lastStatus}]: ${lastErr}`);
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

    const data = await this.requestWithFallback(requestBody);
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

    const data = await this.requestWithFallback(requestBody);
    const candidate = data.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!candidate) {
      return { action: 'direct_dictation', result: rawVoiceInput, original: rawVoiceInput };
    }

    try {
      const parsed = JSON.parse(candidate);
      return {
        action: parsed.action || 'direct_dictation',
        result: parsed.result || rawVoiceInput,
        original: parsed.original || rawVoiceInput
      };
    } catch (e) {
      return {
        action: 'direct_dictation',
        result: candidate.trim(),
        original: rawVoiceInput
      };
    }
  }
}

module.exports = { GeminiTranslator };
