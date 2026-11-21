---
name: "cli-anything-smart-voices-keyboard"
description: >-
  Agent-native command-line interface for Smart Voice Keyboard. Run text predictions, compound word expansion, Banglish-to-Bangla transliteration, voice command intent parsing, Gemini AI translation, and clipboard history inspection.
---

# cli-anything-smart-voices-keyboard

An agent-native command-line interface and interactive REPL harness for Smart Voice Keyboard.
Designed for AI agents with a universal `--json` output flag and stateful REPL mode.

## Installation & Running

```bash
# Direct execution via Node.js
node cli/smartkey.js --help

# Or via npm script
npm run smartkey -- --help

# Or global linking
npm link
smartkey --help
```

## Quick Start for AI Agents

### 1. Multi-Prediction & Compound Expansion
```bash
node cli/smartkey.js predict "চাল" --json
node cli/smartkey.js predict "তা" --json
node cli/smartkey.js predict "how are" --mode en --json
```

**JSON Output Format:**
```json
{
  "success": true,
  "data": {
    "input": "চাল",
    "context": "",
    "mode": "bangla",
    "count": 7,
    "predictions": [
      "চাল",
      "চালতা",
      "চালাক",
      "চালানো",
      "চালচলন",
      "চালু",
      "চালাই"
    ]
  }
}
```

### 2. Phonetic Banglish to Bangla Transliteration
```bash
node cli/smartkey.js phonetic "amar shonar bangla ami tomay bhalobashi" --json
```

**JSON Output:**
```json
{
  "success": true,
  "data": {
    "input": "amar shonar bangla ami tomay bhalobashi",
    "output": "আমার সোনার বাংলা আমি তোমায় ভালোবাসি"
  }
}
```

### 3. Natural Language Voice Command Intent Parsing
```bash
node cli/smartkey.js command "বাংলা করো: Good morning" --json
node cli/smartkey.js command "ফর্মাল ইমেইল বানাও: আমি কাল অফিসে আসব না" --json
```

**JSON Output:**
```json
{
  "success": true,
  "data": {
    "rawVoiceQuery": "বাংলা করো: Good morning",
    "intent": "TRANSLATE_TO_BN",
    "payload": "Good morning",
    "systemPrompt": "You are a professional translator. Translate the text into authentic, natural Bengali. Output ONLY the translated text."
  }
}
```

### 4. Clipboard Manager & Storage Inspection
```bash
node cli/smartkey.js clip list --limit 5 --json
node cli/smartkey.js clip add "পরীক্ষামূলক ক্লিপবোর্ড টেক্সট" --json
node cli/smartkey.js clip search "বাংলা" --json
```

### 5. Interactive REPL Mode
Running without arguments starts an interactive session:
```bash
node cli/smartkey.js
# Enter commands interactively: predict, phonetic, command, or direct typing
```
