/**
 * Smart Voice Keyboard - Phonetic Transliteration Engine
 * Converts Romanized phonetic text (Banglish) into Bengali Unicode characters.
 */

const VOWEL_MAP = {
  'a': 'আ',
  'aa': 'আ',
  'i': 'ই',
  'ee': 'ঈ',
  'I': 'ঈ',
  'u': 'উ',
  'oo': 'ঊ',
  'U': 'ঊ',
  'e': 'এ',
  'oi': 'ঐ',
  'o': 'ও',
  'ou': 'ঔ',
  'rri': 'ঋ'
};

const KAR_MAP = {
  'a': 'া',
  'aa': 'া',
  'i': 'ি',
  'ee': 'ী',
  'I': 'ী',
  'u': 'ু',
  'oo': 'ূ',
  'U': 'ূ',
  'e': 'ে',
  'oi': 'ৈ',
  'o': 'ো',
  'ou': 'ৌ'
};

const CONSONANT_MAP = {
  'k': 'ক',
  'kh': 'খ',
  'g': 'গ',
  'gh': 'ঘ',
  'ng': 'ঙ',
  'c': 'চ',
  'ch': 'ছ',
  'j': 'জ',
  'jh': 'ঝ',
  'T': 'ট',
  'Th': 'ঠ',
  'D': 'ড',
  'Dh': 'ঢ',
  'N': 'ণ',
  't': 'ত',
  'th': 'থ',
  'd': 'দ',
  'dh': 'ধ',
  'n': 'ন',
  'p': 'প',
  'ph': 'ফ',
  'f': 'ফ',
  'b': 'ব',
  'bh': 'ভ',
  'v': 'ভ',
  'm': 'ম',
  'z': 'য',
  'r': 'র',
  'l': 'ল',
  'sh': 'শ',
  'Sh': 'ষ',
  's': 'স',
  'h': 'হ',
  'R': 'ড়',
  'Rh': 'ঢ়',
  'y': 'য়'
};

// Common conjuncts (Juktakkhor)
const CONJUNCT_MAP = {
  'kkh': 'ক্ষ',
  'kkhm': 'ক্ষ্ম',
  'kt': 'ক্ত',
  'kn': 'ক্ন',
  'km': 'ক্ম',
  'ky': 'ক্য',
  'kr': 'ক্র',
  'kl': 'ক্ল',
  'gdh': 'গ্ধ',
  'gn': 'গ্ন',
  'gm': 'গ্ম',
  'gy': 'গ্য',
  'gr': 'গ্র',
  'gl': 'গ্ল',
  'Ngk': 'ঙ্ক',
  'Ngkh': 'ঙ্খ',
  'Ngg': 'ঙ্গ',
  'Nggh': 'ঙ্ঘ',
  'chch': 'চ্চ',
  'chchv': 'চ্ছ',
  'chchh': 'চ্ছ',
  'jj': 'জ্জ',
  'jjh': 'জ্ঝ',
  'jgn': 'জ্ঞ',
  'jy': 'জ্য',
  'jr': 'জ্র',
  'TT': 'ট্ট',
  'DD': 'ড্ড',
  'NT': 'ণ্ট',
  'NTh': 'ণ্ঠ',
  'ND': 'ণ্ড',
  'NDh': 'ণ্ঢ',
  'NN': 'ণ্ণ',
  'tt': 'ত্ত',
  'tth': 'ত্থ',
  'tn': 'ত্ন',
  'tm': 'ত্ম',
  'ty': 'ত্য',
  'tr': 'ত্র',
  'thv': 'থ্ব',
  'dg': 'দ্গ',
  'dgh': 'দ্ঘ',
  'dd': 'দ্দ',
  'ddh': 'দ্ধ',
  'dv': 'দ্ব',
  'db': 'দ্ব',
  'dhm': 'ধ্ম',
  'dhy': 'ধ্য',
  'dhr': 'ধ্র',
  'nt': 'ন্ত',
  'nth': 'ন্থ',
  'nd': 'ন্দ',
  'ndh': 'ন্ধ',
  'nn': 'ন্ন',
  'ny': 'ন্য',
  'nm': 'ন্ম',
  'pt': 'প্ত',
  'pn': 'প্ন',
  'pp': 'প্প',
  'pl': 'প্ল',
  'ps': 'প্স',
  'bd': 'ব্দ',
  'bdh': 'ব্ধ',
  'bb': 'ব্ব',
  'bl': 'ব্ল',
  'by': 'ব্য',
  'br': 'ব্র',
  'mn': 'ম্ন',
  'mp': 'ম্প',
  'mph': 'ম্ফ',
  'mb': 'ম্ব',
  'mbh': 'ম্ভ',
  'mm': 'ম্ম',
  'ml': 'ম্ল',
  'my': 'ম্য',
  'mr': 'ম্র',
  'lk': 'ল্ক',
  'lg': 'ল্গ',
  'ld': 'ল্দ',
  'lb': 'ল্ব',
  'lm': 'ল্ম',
  'ly': 'ল্য',
  'll': 'ল্ল',
  'shch': 'শ্চ',
  'shchv': 'শ্ছ',
  'shn': 'শ্ন',
  'shm': 'শ্ম',
  'shy': 'শ্য',
  'shr': 'শ্র',
  'shl': 'শ্ল',
  'Shk': 'ষ্ক',
  'Shkh': 'ষ্খ',
  'ShT': 'ষ্ট',
  'ShTh': 'ষ্ঠ',
  'ShN': 'ষ্ণ',
  'Shp': 'ষ্প',
  'Shph': 'ষ্ফ',
  'Shm': 'ষ্ম',
  'sk': 'স্ক',
  'skh': 'স্খ',
  'st': 'স্ত',
  'sth': 'স্থ',
  'sp': 'স্প',
  'sph': 'স্ফ',
  'sn': 'স্ন',
  'sm': 'স্ম',
  'sy': 'স্য',
  'sr': 'স্র',
  'sl': 'স্ল',
  'hN': 'হ্ণ',
  'hn': 'হ্ন',
  'hm': 'হ্ম',
  'hy': 'হ্য',
  'hr': 'হ্র',
  'hl': 'হ্ল'
};

const COMMON_WORDS = {
  'ami': 'আমি',
  'amra': 'আমরা',
  'amader': 'আমাদের',
  'tumi': 'তুমি',
  'tomra': 'তোমরা',
  'tomader': 'তোমাদের',
  'apni': 'আপনি',
  'apnara': 'আপনারা',
  'apnader': 'আপনাদের',
  'she': 'সে',
  'tara': 'তারা',
  'tader': 'তাদের',
  'ei': 'এই',
  'oi': 'ওই',
  'shei': 'সেই',
  'ki': 'কী',
  'kemon': 'কেমন',
  'kothay': 'কোথায়',
  'kokhon': 'কখন',
  'keno': 'কেন',
  'kibhabe': 'কীভাবে',
  'achhi': 'আছি',
  'achhen': 'আছেন',
  'achho': 'আছো',
  'bhalo': 'ভালো',
  'dhonnobad': 'ধন্যবাদ',
  'shagotom': 'স্বাগতম',
  'bangla': 'বাংলা',
  'bangladesh': 'বাংলাদেশ',
  'shonar': 'সোনার',
  'desh': 'দেশ',
  'bhalobashi': 'ভালোবাসি',
  'khub': 'খুব',
  'shundor': 'সুন্দর',
  'shobai': 'সবাই',
  'ekhane': 'এখানে',
  'shekhane': 'সেখানে'
};

class PhoneticEngine {
  /**
   * Transliterate a single word from phonetic English to Bengali
   */
  static transliterateWord(rawWord) {
    if (!rawWord || typeof rawWord !== 'string') return '';
    const lowerWord = rawWord.toLowerCase();
    
    // Check dictionary first
    if (COMMON_WORDS[lowerWord]) {
      return COMMON_WORDS[lowerWord];
    }

    let result = '';
    let i = 0;
    const len = rawWord.length;
    let isPrevConsonant = false;

    while (i < len) {
      // 1. Try matching conjuncts (4, 3, 2 chars)
      let matchedConjunct = false;
      for (let clen = 4; clen >= 2; clen--) {
        if (i + clen <= len) {
          const sub = rawWord.substring(i, i + clen);
          if (CONJUNCT_MAP[sub]) {
            result += CONJUNCT_MAP[sub];
            i += clen;
            isPrevConsonant = true;
            matchedConjunct = true;
            break;
          }
        }
      }
      if (matchedConjunct) continue;

      // 2. Try matching 2-char consonants
      if (i + 2 <= len) {
        const sub = rawWord.substring(i, i + 2);
        if (CONSONANT_MAP[sub]) {
          result += CONSONANT_MAP[sub];
          i += 2;
          isPrevConsonant = true;
          continue;
        }
      }

      // 3. Try matching 1-char consonant
      const singleChar = rawWord[i];
      if (CONSONANT_MAP[singleChar]) {
        result += CONSONANT_MAP[singleChar];
        i += 1;
        isPrevConsonant = true;
        continue;
      }

      // 4. Try matching vowels (2-char then 1-char)
      let matchedVowel = false;
      for (let vlen = 2; vlen >= 1; vlen--) {
        if (i + vlen <= len) {
          const sub = rawWord.substring(i, i + vlen).toLowerCase();
          if (isPrevConsonant && KAR_MAP[sub]) {
            result += KAR_MAP[sub];
            i += vlen;
            isPrevConsonant = false;
            matchedVowel = true;
            break;
          } else if (!isPrevConsonant && VOWEL_MAP[sub]) {
            result += VOWEL_MAP[sub];
            i += vlen;
            isPrevConsonant = false;
            matchedVowel = true;
            break;
          }
        }
      }
      if (matchedVowel) continue;

      // 5. Special markers or fallback
      if (singleChar === 'o') {
        // In Bengali inherent vowel 'o' after consonant doesn't add kar
        if (!isPrevConsonant) {
          result += 'অ';
        }
        isPrevConsonant = false;
        i++;
        continue;
      }

      // Fallback: pass character as is (punctuation, numbers, symbols)
      result += singleChar;
      isPrevConsonant = false;
      i++;
    }

    return result;
  }

  /**
   * Transliterate a complete sentence/text
   */
  static transliterateText(text) {
    if (!text) return '';
    // Preserve whitespace and punctuation tokens
    const tokens = text.split(/(\s+|[.,!?;:()\[\]{}'"])/);
    return tokens.map(token => {
      if (/^[a-zA-Z]+$/.test(token)) {
        return this.transliterateWord(token);
      }
      return token;
    }).join('');
  }
}

module.exports = {
  PhoneticEngine,
  COMMON_WORDS,
  VOWEL_MAP,
  KAR_MAP,
  CONSONANT_MAP,
  CONJUNCT_MAP
};
