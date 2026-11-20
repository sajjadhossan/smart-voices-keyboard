const { describe, it } = require('node:test');
const assert = require('node:assert');
const { resolveSuggestionReplacement } = require('../prediction/replacement-engine');

describe('Precision Suggestion Chip Replacement Engine (Phase 7)', () => {

  it('should replace partial English word without duplication', () => {
    // User typed "hel" in text field, candidate is "hello"
    const result1 = resolveSuggestionReplacement('say hel', 'hello');
    assert.strictEqual(result1.deleteCount, 3); // deletes "hel"
    assert.strictEqual(result1.commitText, 'hello ');

    // User typed "th", candidate is "thanks"
    const result2 = resolveSuggestionReplacement('many th', 'thanks');
    assert.strictEqual(result2.deleteCount, 2); // deletes "th"
    assert.strictEqual(result2.commitText, 'thanks ');
  });

  it('should append next-word prediction when preceding text ends in a space', () => {
    // User typed "thank you ", candidate is "very"
    const result = resolveSuggestionReplacement('thank you ', 'very');
    assert.strictEqual(result.deleteCount, 0); // no deletion needed
    assert.strictEqual(result.commitText, 'very ');
  });

  it('should replace partial Bengali word without character duplication', () => {
    // User typed "আম", candidate is "আমি"
    const result1 = resolveSuggestionReplacement('আজ আম', 'আমি');
    assert.strictEqual(result1.deleteCount, 2); // deletes "আম"
    assert.strictEqual(result1.commitText, 'আমি ');

    // User typed "ধন্য", candidate is "ধন্যবাদ"
    const result2 = resolveSuggestionReplacement('আপনাকে ধন্য', 'ধন্যবাদ');
    assert.strictEqual(result2.deleteCount, 4); // deletes "ধন্য"
    assert.strictEqual(result2.commitText, 'ধন্যবাদ ');
  });

  it('should replace misspelled word with autocorrected candidate', () => {
    // User typed "recieve", candidate is "receive"
    const result = resolveSuggestionReplacement('please recieve', 'receive');
    assert.strictEqual(result.deleteCount, 7);
    assert.strictEqual(result.commitText, 'receive ');
  });

  it('should safely handle empty or punctuation text before cursor', () => {
    const resultEmpty = resolveSuggestionReplacement('', 'hello');
    assert.strictEqual(resultEmpty.deleteCount, 0);
    assert.strictEqual(resultEmpty.commitText, 'hello ');

    const resultPunct = resolveSuggestionReplacement('welcome! ', 'friend');
    assert.strictEqual(resultPunct.deleteCount, 0);
    assert.strictEqual(resultPunct.commitText, 'friend ');
  });

});
