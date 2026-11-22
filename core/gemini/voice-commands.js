/**
 * Smart Voice Keyboard - Natural Language Voice Command & Prompt Template Engine
 * Supports Bengali & English conversational command recognition and Gemini AI execution.
 */

const { GeminiTranslator } = require('./translator');

const COMMAND_PATTERNS = [
  {
    type: 'TRANSLATE_TO_BN',
    patterns: [
      /^(বাংলা\s*করো|বাংলায়\s*অনুবাদ\s*করো|translate\s*to\s*bangla|translate\s*to\s*bengali)[:\s]+(.+)/i,
      /(.+)\s+(কে\s*বাংলা\s*করো|কে\s*বাংলায়\s*বলো)$/i
    ],
    systemPrompt: 'You are a professional translator. Translate the text into authentic, natural Bengali. Output ONLY the translated text.'
  },
  {
    type: 'TRANSLATE_TO_EN',
    patterns: [
      /^(ইংরেজি\s*করো|ইংরেজিতে\s*অনুবাদ\s*করো|translate\s*to\s*english)[:\s]+(.+)/i,
      /(.+)\s+(কে\s*ইংরেজি\s*করো|কে\s*ইংরেজিতে\s*বলো)$/i
    ],
    systemPrompt: 'You are a professional translator. Translate the text into fluent, grammatically perfect English. Output ONLY the translated text.'
  },
  {
    type: 'FORMAL_EMAIL',
    patterns: [
      /^(ফর্মাল\s*ইমেইল\s*বানাও|ইমেইল\s*লেখো|make\s*formal\s*email|write\s*an?\s*email)[:\s]+(.+)/i,
      /^(official\s*email\s*about|formal\s*email\s*about)[:\s]+(.+)/i
    ],
    systemPrompt: 'You are an executive business communication assistant. Convert the given message into a complete, professional, courteous formal email including Subject and Body. Output ONLY the email content.'
  },
  {
    type: 'FIX_GRAMMAR',
    patterns: [
      /^(বানান\s*ঠিক\s*করো|ব্যাকরণ\s*ঠিক\s*করো|ভুল\s*সংশোধন\s*করো|fix\s*grammar|correct\s*spelling)[:\s]+(.+)/i,
      /^(proofread|polish)[:\s]+(.+)/i
    ],
    systemPrompt: 'You are a master editor for Bengali and English. Correct all grammatical errors, typos, and awkward phrasing while preserving original intent. Output ONLY the polished text.'
  },
  {
    type: 'SUMMARIZE_BULLETS',
    patterns: [
      /^(পয়েন্ট\s*আকারে\s*সাজাও|সংক্ষেপে\s*বলো|সামারি\s*করো|summarize|bullet\s*points)[:\s]+(.+)/i
    ],
    systemPrompt: 'Summarize the following text into clear, concise bullet points in the same language. Output ONLY the bullet list.'
  },
  {
    type: 'MAKE_CASUAL',
    patterns: [
      /^(সহজ\s*করে\s*বলো|ক্যাজুয়াল\s*করো|make\s*casual|simplify)[:\s]+(.+)/i
    ],
    systemPrompt: 'Rewrite the given text in a friendly, relaxed, conversational tone. Output ONLY the rewritten text.'
  }
];

class VoiceCommandEngine {
  constructor(apiKey = process.env.GEMINI_API_KEY) {
    this.apiKey = apiKey;
    this.translator = new GeminiTranslator(apiKey);
    this.baseUrl = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent';
  }

  setApiKey(key) {
    this.apiKey = key;
    this.translator.setApiKey(key);
  }

  /**
   * Detect command type and extract payload from spoken speech
   * @param {string} spokenText
   * @returns {{isCommand: boolean, type: string, payload: string, systemPrompt?: string}}
   */
  detectCommand(spokenText) {
    if (!spokenText || typeof spokenText !== 'string') {
      return { isCommand: false, type: 'DIRECT_DICTATION', payload: '' };
    }

    const trimmed = spokenText.trim();

    for (const cmd of COMMAND_PATTERNS) {
      for (const pattern of cmd.patterns) {
        const match = trimmed.match(pattern);
        if (match) {
          const payload = (match[2] || match[1] || '').trim();
          return {
            isCommand: true,
            type: cmd.type,
            payload: payload || trimmed,
            systemPrompt: cmd.systemPrompt
          };
        }
      }
    }

    return {
      isCommand: false,
      type: 'DIRECT_DICTATION',
      payload: trimmed
    };
  }

  /**
   * Execute voice command or smart preset with Gemini AI
   * @param {string} text - Spoken speech or selected text
   * @param {string} [forcedType] - Optional override type (e.g. from UI buttons)
   * @returns {Promise<{action: string, result: string, original: string}>}
   */
  async process(text, forcedType = null) {
    if (!text || !text.trim()) {
      return { action: 'NONE', result: '', original: text };
    }

    const detected = forcedType 
      ? { isCommand: true, type: forcedType, payload: text.trim(), systemPrompt: this.getPromptForType(forcedType) }
      : this.detectCommand(text);

    if (!detected.isCommand) {
      // Regular speech dictation, return as is
      return { action: 'DIRECT_DICTATION', result: text.trim(), original: text };
    }

    if (!this.apiKey) {
      throw new Error('GEMINI_API_KEY_NOT_CONFIGURED');
    }

    // Direct translation routing
    if (detected.type === 'TRANSLATE_TO_BN') {
      const translated = await this.translator.translate(detected.payload, 'en', 'bn');
      return { action: detected.type, result: translated, original: text };
    }

    if (detected.type === 'TRANSLATE_TO_EN') {
      const translated = await this.translator.translate(detected.payload, 'bn', 'en');
      return { action: detected.type, result: translated, original: text };
    }

    // Specialized Gemini Generation
    const requestBody = {
      contents: [
        {
          role: 'user',
          parts: [{ text: `${detected.systemPrompt}\n\nInput Text:\n${detected.payload}` }]
        }
      ],
      generationConfig: {
        temperature: 0.3,
        maxOutputTokens: 1024
      }
    };

    const data = await this.translator.requestWithFallback(requestBody);
    const resultText = data.candidates?.[0]?.content?.parts?.[0]?.text;
    return {
      action: detected.type,
      result: resultText ? resultText.trim() : detected.payload,
      original: text
    };
  }

  getPromptForType(type) {
    const item = COMMAND_PATTERNS.find(c => c.type === type);
    return item ? item.systemPrompt : 'Process the text clearly and accurately.';
  }
}

module.exports = { VoiceCommandEngine, COMMAND_PATTERNS };
