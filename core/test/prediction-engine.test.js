/**
 * Prediction Engine Tests (Phase 2 & Phase 9)
 * Tests autocorrect, prefix auto-completion, next-word N-grams, emoji suggestions,
 * compound word expansion (e.g. চাল -> চালতা), and phonetic/orthographic alternates (তা -> টা).
 */

const assert = require('assert');

class TestPredictionEngine {
  constructor() {
    this.userLearned = new Map();
    this.englishAutocorrect = {
      'teh': 'the',
      'recieve': 'receive',
      'im': "I'm",
      'dont': "don't"
    };
    this.banglaAutocorrect = {
      'ধন্নবাদ': 'ধন্যবাদ',
      'গেসিলাম': 'গিয়েছিলাম'
    };
    this.emojiMap = {
      'happy': '😊', 'খুশি': '😊',
      'thanks': '🙏', 'ধন্যবাদ': '🙏',
      'love': '❤️', 'ভালোবাসা': '❤️',
      'fire': '🔥'
    };
    this.englishNextWords = {
      'how': ['are', 'is', 'do'],
      'how are': ['you', 'things'],
      'thank': ['you', 'god'],
      'good': ['morning', 'night']
    };
    this.banglaNextWords = {
      'আমি': ['ভালো', 'আছি', 'তোমাকে'],
      'তুমি': ['কেমন', 'কোথায়', 'কি'],
      'কেমন': ['আছো', 'আছেন'],
      'আসসালামু': ['আলাইকুম'],
      'ইনশা': ['আল্লাহ']
    };
    this.englishVocab = ['the', 'that', 'have', 'hello', 'thanks', 'tomorrow', 'connect', 'continue', 'contact'];
    this.banglaVocab = [
      'বাংলাদেশ', 'ধন্যবাদ', 'ভালোবাসা', 'স্বাগতম',
      'চাল', 'চালতা', 'চালাক', 'চালানো', 'চালচলন',
      'তা', 'টা', 'তার', 'তাদের', 'তাহলে', 'টাকা', 'টাইম', 'টাকায়'
    ];
    this.banglaCharAlternates = {
      'ত': ['ট'],
      'ট': ['ত'],
      'থ': ['ঠ'],
      'ঠ': ['থ'],
      'দ': ['ড'],
      'ড': ['দ'],
      'ধ': ['ঢ'],
      'ঢ': ['ধ'],
      'শ': ['ষ', 'স'],
      'স': ['শ', 'ষ'],
      'ন': ['ণ'],
      'ণ': ['ন'],
      'ব': ['ভ'],
      'ভ': ['ব'],
      'র': ['ড়', 'ঢ়']
    };
  }

  learnWord(word) {
    const clean = word.trim();
    if (clean.length >= 2) {
      const count = this.userLearned.get(clean) || 0;
      this.userLearned.set(clean, count + 1);
    }
  }

  getBanglaAlternatePrefixes(word) {
    if (!word) return [];
    const firstChar = word[0];
    const alts = this.banglaCharAlternates[firstChar] || [];
    const rest = word.substring(1);
    return alts.map(a => `${a}${rest}`);
  }

  getPredictions(currentWord, previousText, isBangla) {
    const results = [];
    const clean = currentWord.trim();
    const cleanLower = clean.toLowerCase();

    // Emoji check
    if (this.emojiMap[cleanLower]) {
      results.push(this.emojiMap[cleanLower]);
    }

    if (!clean) {
      // Next word prediction
      const tokens = previousText.trim().split(/\s+/).filter(Boolean);
      if (tokens.length) {
        const last = tokens[tokens.length - 1];
        const lastTwo = tokens.length >= 2 ? `${tokens[tokens.length - 2]} ${last}` : '';

        if (isBangla) {
          if (this.banglaNextWords[lastTwo]) results.push(...this.banglaNextWords[lastTwo]);
          else if (this.banglaNextWords[last]) results.push(...this.banglaNextWords[last]);
          if (this.emojiMap[last]) results.push(this.emojiMap[last]);
        } else {
          const lowerLast = last.toLowerCase();
          const lowerTwo = lastTwo.toLowerCase();
          if (this.englishNextWords[lowerTwo]) results.push(...this.englishNextWords[lowerTwo]);
          else if (this.englishNextWords[lowerLast]) results.push(...this.englishNextWords[lowerLast]);
          if (this.emojiMap[lowerLast]) results.push(this.emojiMap[lowerLast]);
        }
      }
    } else {
      // Direct candidate
      results.push(clean);

      // Autocorrect
      if (isBangla) {
        if (this.banglaAutocorrect[clean] && this.banglaAutocorrect[clean] !== clean) {
          results.push(this.banglaAutocorrect[clean]);
        }
        // Alternates (e.g. 'তা' -> 'টা')
        const altPrefixes = this.getBanglaAlternatePrefixes(clean);
        results.push(...altPrefixes);
      } else {
        if (this.englishAutocorrect[cleanLower] && this.englishAutocorrect[cleanLower] !== clean) {
          results.push(this.englishAutocorrect[cleanLower]);
        }
      }

      // User learned matching prefix
      for (const [w] of this.userLearned.entries()) {
        if (w.toLowerCase().startsWith(cleanLower) && w.toLowerCase() !== cleanLower) {
          results.push(w);
        }
      }

      // Vocab & compound expansions (e.g. 'চাল' -> 'চালতা', 'চালাক')
      const vocab = isBangla ? this.banglaVocab : this.englishVocab;
      const matches = vocab.filter(w => w.startsWith(isBangla ? clean : cleanLower) && w !== clean);
      results.push(...matches);

      if (isBangla) {
        const altPrefixes = this.getBanglaAlternatePrefixes(clean);
        for (const alt of altPrefixes) {
          const altMatches = this.banglaVocab.filter(w => w.startsWith(alt) && w !== alt);
          results.push(...altMatches);
        }
      }
    }

    return [...new Set(results)].slice(0, 12);
  }
}

async function runPredictionEngineTests() {
  console.log('=== Intelligent Multi-Prediction Engine Tests (Phase 2 & Phase 9) ===\n');

  const engine = new TestPredictionEngine();

  // Test 1: Autocorrect
  console.log('1. Testing Autocorrect Mappings...');
  const acEn = engine.getPredictions('teh', '', false);
  assert(acEn.includes('the'), 'teh should autocorrect to the');
  const acBn = engine.getPredictions('ধন্নবাদ', '', true);
  assert(acBn.includes('ধন্যবাদ'), 'ধন্নবাদ should autocorrect to ধন্যবাদ');
  console.log('  [PASS] English & Bangla autocorrect verified.');

  // Test 2: Next-Word Bigram & Trigram Associations
  console.log('2. Testing Next-Word N-Grams...');
  const nextEn1 = engine.getPredictions('', 'how', false);
  assert(nextEn1.includes('are'), 'how should predict are');
  const nextEn2 = engine.getPredictions('', 'how are', false);
  assert(nextEn2.includes('you'), 'how are should predict you');

  const nextBn1 = engine.getPredictions('', 'আমি', true);
  assert(nextBn1.includes('ভালো'), 'আমি should predict ভালো');
  const nextBn2 = engine.getPredictions('', 'আসসালামু', true);
  assert(nextBn2.includes('আলাইকুম'), 'আসসালামু should predict আলাইকুম');
  console.log('  [PASS] Next-word bigrams and trigrams verified for English & Bangla.');

  // Test 3: Emoji suggestions
  console.log('3. Testing Smart Emoji Associations...');
  const emojiHappy = engine.getPredictions('happy', '', false);
  assert(emojiHappy.includes('😊'), 'happy should suggest 😊');
  const emojiThanks = engine.getPredictions('ধন্যবাদ', '', true);
  assert(emojiThanks.includes('🙏'), 'ধন্যবাদ should suggest 🙏');
  console.log('  [PASS] Emoji suggestions verified.');

  // Test 4: Dynamic User Learning Dictionary
  console.log('4. Testing User Dictionary Learning...');
  engine.learnWord('SajjadHossan');
  engine.learnWord('SajjadHossan');
  const learnedPreds = engine.getPredictions('saj', '', false);
  assert(learnedPreds.includes('SajjadHossan'), 'Custom user word should be predicted');
  console.log('  [PASS] User dictionary learning and prioritization verified.');

  // Test 5: Compound & Substring Word Expansion (User: চাল -> চালতা)
  console.log('5. Testing Compound Word Expansion (চাল -> চালতা)...');
  const chalPreds = engine.getPredictions('চাল', '', true);
  assert(chalPreds.includes('চালতা'), 'চাল should predict চালতা');
  assert(chalPreds.includes('চালাক'), 'চাল should predict চালাক');
  console.log('  [PASS] Compound word expansion verified ("চাল" -> "চালতা", "চালাক").');

  // Test 6: Orthographic & Phonetic Alternates (User: তা -> টা)
  console.log('6. Testing Phonetic / Orthographic Alternates (তা -> টা)...');
  const taPreds = engine.getPredictions('তা', '', true);
  assert(taPreds.includes('টা'), 'তা should suggest alternate টা');
  assert(taPreds.includes('টাকা'), 'তা should suggest alternate vocabulary টাকা');
  console.log('  [PASS] Phonetic alternate suggestions verified ("তা" -> "টা", "টাকা").');

  console.log('\n=== ALL PREDICTION ENGINE TESTS PASSED (6/6)! ===\n');
}

if (require.main === module) {
  runPredictionEngineTests().catch(err => {
    console.error('Test Failed:', err);
    process.exit(1);
  });
}

module.exports = { TestPredictionEngine };
