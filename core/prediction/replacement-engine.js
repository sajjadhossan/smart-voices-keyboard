/**
 * Smart Voices Keyboard - Precision Suggestion Chip Replacement Engine (Phase 7)
 * Eliminates character duplication and cleanly replaces partial tokens or appends next words.
 */

/**
 * Calculates how many characters to delete before the cursor when inserting a suggestion.
 * @param {string} textBeforeCursor - Substring immediately preceding cursor
 * @param {string} candidate - The chosen suggestion word
 * @returns {{ deleteCount: number, commitText: string }}
 */
function resolveSuggestionReplacement(textBeforeCursor, candidate) {
  if (!candidate || typeof candidate !== 'string') {
    return { deleteCount: 0, commitText: '' };
  }

  const cleanCand = candidate.trim();
  if (!textBeforeCursor || typeof textBeforeCursor !== 'string' || textBeforeCursor.length === 0) {
    return { deleteCount: 0, commitText: cleanCand + ' ' };
  }

  // If text ends with whitespace, this is a next-word prediction -> delete 0, commit candidate
  if (/\s$/.test(textBeforeCursor)) {
    return { deleteCount: 0, commitText: cleanCand + ' ' };
  }

  // Find partial word before cursor
  let idx = textBeforeCursor.length;
  const isPunctOrSpace = (ch) => /\s|[\.,!?;:\-–—"'`\(\)\[\]\{\}।॥]/.test(ch);

  while (idx > 0 && !isPunctOrSpace(textBeforeCursor[idx - 1])) {
    idx--;
  }

  const partialWord = textBeforeCursor.substring(idx);

  // If candidate starts with partial word (e.g. "hel" -> "hello", "আম" -> "আমি")
  if (partialWord.length > 0 && cleanCand.toLowerCase().startsWith(partialWord.toLowerCase())) {
    return {
      deleteCount: partialWord.length,
      commitText: cleanCand + ' '
    };
  }

  // If candidate is a direct autocorrect replacement for the partial word
  if (partialWord.length > 0) {
    return {
      deleteCount: partialWord.length,
      commitText: cleanCand + ' '
    };
  }

  return { deleteCount: 0, commitText: cleanCand + ' ' };
}

module.exports = {
  resolveSuggestionReplacement
};
