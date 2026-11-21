/**
 * Smart Voice Keyboard - Intelligent Multi-Prediction Engine
 * Handles autocomplete, autocorrect, prefix alternates, compound expansions, and N-gram predictions.
 */

class PredictionEngine {
  constructor() {
    this.userLearned = new Map();

    this.englishAutocorrect = {
      'teh': 'the',
      'recieve': 'receive',
      'im': "I'm",
      'dont': "don't",
      'cant': "can't",
      'wont': "won't"
    };

    this.banglaAutocorrect = {
      'ধন্নবাদ': 'ধন্যবাদ',
      'গেসিলাম': 'গিয়েছিলাম',
      'আসতেসি': 'আসছি',
      'করসি': 'করেছি'
    };

    this.emojiMap = {
      'happy': '😊', 'খুশি': '😊',
      'thanks': '🙏', 'ধন্যবাদ': '🙏',
      'love': '❤️', 'ভালোবাসা': '❤️',
      'fire': '🔥', 'আগুন': '🔥',
      'cool': '😎', 'স্মার্ট': '😎',
      'sad': '😢', 'দুঃখ': '😢'
    };

    this.englishNextWords = {
      'how': ['are', 'is', 'do'],
      'how are': ['you', 'things'],
      'thank': ['you', 'god'],
      'good': ['morning', 'night', 'afternoon'],
      'what': ['is', 'are', 'happened']
    };

    this.banglaNextWords = {
      'আমি': ['ভালো', 'আছি', 'তোমাকে', 'ভাত'],
      'তুমি': ['কেমন', 'কোথায়', 'কি', 'কেমন আছো'],
      'কেমন': ['আছো', 'আছেন'],
      'আসসালামু': ['আলাইকুম'],
      'ইনশা': ['আল্লাহ'],
      'শুভ': ['সকাল', 'রাত্রি', 'সন্ধ্যা']
    };

    this.englishVocab = [
      'the', 'that', 'have', 'hello', 'thanks', 'tomorrow', 'connect', 'continue', 'contact',
      'keyboard', 'language', 'prediction', 'project', 'system', 'developer', 'application'
    ];

    this.banglaVocab = [
      'বাংলাদেশ', 'ধন্যবাদ', 'ভালোবাসা', 'স্বাগতম',
      'চাল', 'চালতা', 'চালাক', 'চালানো', 'চালচলন', 'চালু', 'চালাই', 'চালিয়ে', 'চালাচ্ছে',
      'তা', 'টা', 'তার', 'তাদের', 'তাহলে', 'টাকা', 'টাইম', 'টাকায়', 'ট্যাক্স',
      'খাবার', 'মানুষ', 'জীবন', 'কাজ', 'অফিস', 'মোবাইল', 'কথা', 'কিবোর্ড'
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
      'ষ': ['শ', 'স'],
      'স': ['শ', 'ষ'],
      'ন': ['ণ'],
      'ণ': ['ন'],
      'ব': ['ভ'],
      'ভ': ['ব'],
      'র': ['ড়', 'ঢ়']
    };
  }

  learnWord(word) {
    const clean = (word || '').trim();
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

  getPredictions(currentWord = '', previousText = '', isBangla = true, limit = 12) {
    const results = [];
    const clean = (currentWord || '').trim();
    const cleanLower = clean.toLowerCase();

    // 1. Emoji check
    if (this.emojiMap[cleanLower]) {
      results.push(this.emojiMap[cleanLower]);
    }

    if (!clean) {
      // 2. Next-word prediction from context
      const tokens = (previousText || '').trim().split(/\s+/).filter(Boolean);
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
      // 3. Current active token
      results.push(clean);

      // 4. Autocorrect
      if (isBangla) {
        if (this.banglaAutocorrect[clean] && this.banglaAutocorrect[clean] !== clean) {
          results.push(this.banglaAutocorrect[clean]);
        }
        // Orthographic & phonetic alternates (e.g. 'তা' -> 'টা')
        const altPrefixes = this.getBanglaAlternatePrefixes(clean);
        results.push(...altPrefixes);
      } else {
        if (this.englishAutocorrect[cleanLower] && this.englishAutocorrect[cleanLower] !== clean) {
          results.push(this.englishAutocorrect[cleanLower]);
        }
      }

      // 5. User-learned matching prefixes
      for (const [w] of this.userLearned.entries()) {
        if (w.toLowerCase().startsWith(cleanLower) && w.toLowerCase() !== cleanLower) {
          results.push(w);
        }
      }

      // 6. Vocabulary & compound word expansion (e.g. 'চাল' -> 'চালতা', 'চালাক', 'চালানো')
      const vocab = isBangla ? this.banglaVocab : this.englishVocab;
      const matches = vocab.filter(w => w.startsWith(isBangla ? clean : cleanLower) && w !== clean);
      results.push(...matches);

      // 7. Alternate prefixes compound expansions
      if (isBangla) {
        const altPrefixes = this.getBanglaAlternatePrefixes(clean);
        for (const alt of altPrefixes) {
          const altMatches = this.banglaVocab.filter(w => w.startsWith(alt) && w !== alt);
          results.push(...altMatches);
        }
      }
    }

    return [...new Set(results)].slice(0, limit);
  }
}

module.exports = { PredictionEngine };
