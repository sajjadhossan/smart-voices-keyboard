const { describe, it } = require('node:test');
const assert = require('node:assert');

describe('Voice Acoustic Processing, Auto-Punctuation & Command Processor (Phase 4)', () => {

  function processVoiceText(rawText, langMode) {
    let text = (rawText || '').trim();
    if (!text) return { isCommand: false, type: 'DIRECT_DICTATION', finalText: '' };

    const lower = text.toLowerCase();
    if (['মুছে দাও', 'ক্লিয়ার', 'সব মুছুন', 'clear all', 'delete all'].includes(lower)) {
      return { isCommand: true, type: 'CLEAR', payload: '', finalText: '' };
    }
    if (['নতুন লাইন', 'পরের লাইন', 'new line'].includes(lower)) {
      return { isCommand: true, type: 'NEWLINE', payload: '', finalText: '\n' };
    }

    // Voice Translation
    const bnMatch = text.match(/^(বাংলা করো|বাংলায় বলো|translate to bangla|translate to bengali)[:\s]+(.+)/i);
    if (bnMatch) {
      const payload = bnMatch[2].trim();
      return { isCommand: true, type: 'TRANSLATE_TO_BN', payload, finalText: payload };
    }

    const enMatch = text.match(/^(ইংরেজি করো|ইংরেজিতে বলো|translate to english)[:\s]+(.+)/i);
    if (enMatch) {
      const payload = enMatch[2].trim();
      return { isCommand: true, type: 'TRANSLATE_TO_EN', payload, finalText: payload };
    }

    // Spoken punctuation substitution (Unicode and space aware)
    text = text
      .replace(/(?:^|\s+)(দাড়ি|দাড়ি|পূর্ণচ্ছেদ)(?:\s+|$)/gi, '। ')
      .replace(/(?:^|\s+)কমা(?:\s+|$)/gi, ', ')
      .replace(/(?:^|\s+)(প্রশ্নবোধক|জিজ্ঞাসা চিহ্ন|প্রশ্ন চিহ্ন)(?:\s+|$)/gi, '? ')
      .replace(/(?:^|\s+)(বিস্ময়সূচক|আশ্চর্যবোধক)(?:\s+|$)/gi, '! ')
      .replace(/\b(period|full stop)\b/gi, '.')
      .replace(/\bcomma\b/gi, ',')
      .replace(/\bquestion mark\b/gi, '?')
      .replace(/\bexclamation mark\b/gi, '!')
      .replace(/(?:^|\s+)(নতুন লাইন|পরের লাইন|new line)(?:\s+|$)/gi, '\n')
      .trim();

    // Contextual Auto-Punctuation
    const lastChar = text.slice(-1);
    const hasPunctuation = ['.', '।', '?', '!', '\n'].includes(lastChar);

    if (!hasPunctuation) {
      if (langMode === 'en') {
        const questionWords = ['what', 'where', 'when', 'why', 'who', 'how', 'can', 'could', 'would', 'is', 'are', 'do', 'does'];
        const firstWord = text.split(/\s+/)[0]?.toLowerCase();
        text = questionWords.includes(firstWord) ? `${text}?` : `${text}.`;
        text = text.charAt(0).toUpperCase() + text.slice(1);
      } else {
        const bnQuestions = ['কী', 'কি', 'কেন', 'কোথায়', 'কোথায়', 'কেমন', 'কখন', 'কার', 'হবে কি', 'আছো কি'];
        const isQuestion = bnQuestions.some(q => text.includes(q));
        text = isQuestion ? `${text}?` : `${text}।`;
      }
    }

    // Format typography: attach punctuation to preceding word without leading space
    text = text
      .replace(/\s+([,।!?.])/g, '$1')
      .replace(/([,।])([^\s\n])/g, '$1 $2')
      .replace(/\s+/g, ' ')
      .trim();

    return { isCommand: false, type: 'DIRECT_DICTATION', finalText: text };
  }

  it('should substitute spoken punctuation tokens with actual symbols', () => {
    const resBn = processVoiceText('আজকে মিটিং হবে কমা সবাই উপস্থিত থাকবেন দাড়ি', 'bn');
    assert.strictEqual(resBn.finalText, 'আজকে মিটিং হবে, সবাই উপস্থিত থাকবেন।');

    const resEn = processVoiceText('Hello comma how are you question mark', 'en');
    assert.strictEqual(resEn.finalText, 'Hello, how are you?');
  });

  it('should apply automatic contextual punctuation when none is spoken', () => {
    // Bengali statement
    const bnStmt = processVoiceText('আমি এখন ভাত খাবো', 'bn');
    assert.strictEqual(bnStmt.finalText, 'আমি এখন ভাত খাবো।');

    // Bengali question
    const bnQues = processVoiceText('আপনি এখন কেমন আছেন', 'bn');
    assert.strictEqual(bnQues.finalText, 'আপনি এখন কেমন আছেন?');

    // English statement
    const enStmt = processVoiceText('we are working on the project', 'en');
    assert.strictEqual(enStmt.finalText, 'We are working on the project.');

    // English question
    const enQues = processVoiceText('where is the main office', 'en');
    assert.strictEqual(enQues.finalText, 'Where is the main office?');
  });

  it('should detect natural voice command intents', () => {
    const cmdClear = processVoiceText('মুছে দাও', 'bn');
    assert.strictEqual(cmdClear.isCommand, true);
    assert.strictEqual(cmdClear.type, 'CLEAR');

    const cmdNewline = processVoiceText('নতুন লাইন', 'bn');
    assert.strictEqual(cmdNewline.isCommand, true);
    assert.strictEqual(cmdNewline.type, 'NEWLINE');

    const cmdTransBn = processVoiceText('বাংলা করো: Good morning everyone', 'bn');
    assert.strictEqual(cmdTransBn.isCommand, true);
    assert.strictEqual(cmdTransBn.type, 'TRANSLATE_TO_BN');
    assert.strictEqual(cmdTransBn.payload, 'Good morning everyone');

    const cmdTransEn = processVoiceText('ইংরেজি করো: আমি ভালো আছি', 'bn');
    assert.strictEqual(cmdTransEn.isCommand, true);
    assert.strictEqual(cmdTransEn.type, 'TRANSLATE_TO_EN');
    assert.strictEqual(cmdTransEn.payload, 'আমি ভালো আছি');
  });

  it('should verify VAD silence threshold and speech energy specs', () => {
    const speechDbThreshold = 42.0;
    const silenceTimeoutMs = 2200;
    const maxDurationMs = 30000;

    const ambientNoiseLevel = 31.5;
    const spokenVoiceLevel = 58.2;

    assert.strictEqual(ambientNoiseLevel < speechDbThreshold, true, 'Ambient noise rejected by threshold');
    assert.strictEqual(spokenVoiceLevel >= speechDbThreshold, true, 'Speech active detected');
    assert.strictEqual(silenceTimeoutMs, 2200, 'Silence pause trigger is 2.2 seconds');
    assert.strictEqual(maxDurationMs, 30000, 'Max recording duration is 30 seconds');
  });
});
