#!/usr/bin/env node
/**
 * Smart Voice Keyboard - Agent-Native CLI (CLI-Anything Standard)
 * Provides both structured subcommand CLI and stateful interactive REPL mode.
 */

const readline = require('readline');
const path = require('path');
const fs = require('fs');

const { PhoneticEngine } = require('../core/phonetic/phonetic-engine');
const { PredictionEngine } = require('../core/prediction/prediction-engine');
const { VoiceCommandEngine } = require('../core/gemini/voice-commands');
const { GeminiTranslator } = require('../core/gemini/translator');
const { ClipboardStore } = require('../core/clipboard/clipboard-store');

const VERSION = '2.3.0';

// ANSI Colors for Human-Friendly REPL & Terminal Display
const COLORS = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  dim: '\x1b[2m',
  green: '\x1b[32m',
  cyan: '\x1b[36m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m'
};

const predictionEngine = new PredictionEngine();
const voiceCommandEngine = new VoiceCommandEngine();
const clipboardStore = new ClipboardStore();

function printBanner() {
  console.log(`${COLORS.cyan}┌─────────────────────────────────────────────────────────────┐${COLORS.reset}`);
  console.log(`${COLORS.cyan}│${COLORS.bright}  🎙️  Smart Voice Keyboard CLI (v${VERSION})                      ${COLORS.cyan}│${COLORS.reset}`);
  console.log(`${COLORS.cyan}│${COLORS.dim}  Agent-Native Harness & Interactive REPL Shell               ${COLORS.cyan}│${COLORS.reset}`);
  console.log(`${COLORS.cyan}└─────────────────────────────────────────────────────────────┘${COLORS.reset}`);
}

function parseFlags(args) {
  const flags = {
    json: false,
    limit: 12,
    mode: 'bn',
    from: 'bn',
    to: 'en',
    context: '',
    key: process.env.GEMINI_API_KEY || ''
  };
  const positionals = [];

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--json') {
      flags.json = true;
    } else if (arg === '--limit' && i + 1 < args.length) {
      flags.limit = parseInt(args[++i], 10) || 12;
    } else if (arg === '--mode' && i + 1 < args.length) {
      flags.mode = args[++i];
    } else if (arg === '--from' && i + 1 < args.length) {
      flags.from = args[++i];
    } else if (arg === '--to' && i + 1 < args.length) {
      flags.to = args[++i];
    } else if (arg === '--context' && i + 1 < args.length) {
      flags.context = args[++i];
    } else if (arg === '--key' && i + 1 < args.length) {
      flags.key = args[++i];
    } else if (arg.startsWith('--')) {
      // ignore or capture unknown
    } else {
      positionals.push(arg);
    }
  }

  return { flags, positionals };
}

function outputResult(result, isJson) {
  if (isJson) {
    console.log(JSON.stringify(result, null, 2));
  } else {
    if (result.success) {
      if (result.message) console.log(`${COLORS.green}✓ ${result.message}${COLORS.reset}`);
      if (result.data !== undefined) {
        if (typeof result.data === 'string') {
          console.log(result.data);
        } else if (Array.isArray(result.data)) {
          console.log(result.data.map(item => typeof item === 'object' ? JSON.stringify(item) : `  • ${item}`).join('\n'));
        } else {
          console.dir(result.data, { depth: null, colors: true });
        }
      }
    } else {
      console.error(`${COLORS.red}✗ Error: ${result.message || 'Operation failed'}${COLORS.reset}`);
      if (result.error) console.error(`${COLORS.dim}${result.error}${COLORS.reset}`);
    }
  }
}

// Subcommands Handlers
async function handlePredict(positionals, flags) {
  const token = positionals.join(' ');
  const isBangla = flags.mode.toLowerCase() !== 'en';
  const predictions = predictionEngine.getPredictions(token, flags.context, isBangla, flags.limit);

  return {
    success: true,
    data: {
      input: token,
      context: flags.context,
      mode: isBangla ? 'bangla' : 'english',
      count: predictions.length,
      predictions
    }
  };
}

async function handlePhonetic(positionals) {
  const text = positionals.join(' ');
  const transliterated = PhoneticEngine.transliterateText(text);

  return {
    success: true,
    data: {
      input: text,
      output: transliterated
    }
  };
}

async function handleCommand(positionals) {
  const query = positionals.join(' ');
  const detected = voiceCommandEngine.detectCommand(query);

  return {
    success: true,
    data: {
      rawVoiceQuery: query,
      intent: detected.type,
      payload: detected.payload,
      systemPrompt: detected.systemPrompt
    }
  };
}

async function handleTranslate(positionals, flags) {
  const text = positionals.join(' ');
  if (!text) {
    return { success: false, message: 'Please provide text to translate' };
  }

  const translator = new GeminiTranslator(flags.key);
  try {
    const result = await translator.translateText(text, flags.from, flags.to);
    return {
      success: true,
      data: {
        input: text,
        from: flags.from,
        to: flags.to,
        translated: result
      }
    };
  } catch (err) {
    return {
      success: false,
      message: 'Translation error (API key may be missing or rate-limited)',
      error: err.message
    };
  }
}

async function handleClip(positionals, flags) {
  const action = positionals[0] || 'list';
  const arg = positionals.slice(1).join(' ');

  if (action === 'add') {
    if (!arg) return { success: false, message: 'Text required to add clip' };
    const item = clipboardStore.addClip(arg, 'cli');
    return { success: true, message: 'Clip saved', data: item };
  }

  if (action === 'search') {
    const results = clipboardStore.search(arg || flags.query || '');
    return { success: true, data: results.slice(0, flags.limit) };
  }

  if (action === 'pin') {
    const item = clipboardStore.togglePin(arg);
    return { success: true, message: 'Pin toggled', data: item };
  }

  // list
  const clips = clipboardStore.getAll();
  return {
    success: true,
    data: {
      total: clips.length,
      clips: clips.slice(0, flags.limit)
    }
  };
}

function handleStatus() {
  return {
    success: true,
    data: {
      name: 'smart-voices-keyboard',
      version: VERSION,
      status: 'active',
      features: [
        'Multi-Prediction & Compound Expansion',
        'Phonetic Banglish to Bangla Conversion',
        'Voice Command Parsing & Gemini AI Translation',
        'Unlimited Clipboard & Cross-Device Sync',
        'In-App Auto Updater & Permanent Keystore'
      ],
      supportedPlatforms: ['android', 'windows', 'macos', 'web']
    }
  };
}

function printHelp() {
  console.log(`
Usage: smartkey <command> [options]

Commands:
  predict <token>       Predict completions, compound words, and alternates
  phonetic <text>       Transliterate Banglish phonetic text to Bangla
  translate <text>      Translate text between Bangla and English
  command <voiceText>   Detect voice command intent and extract payload
  clip <action>         Clipboard manager (list, add <text>, search <q>, pin <id>)
  status                Show engine version, platform readiness and features
  repl                  Start interactive REPL shell (default when run without args)

Options:
  --json                Output structured machine-readable JSON for agents
  --context <text>      Context preceding current token for next-word predictions
  --mode <bn|en>        Language mode for prediction (default: bn)
  --from <lang>         Source language for translation (default: bn)
  --to <lang>           Target language for translation (default: en)
  --limit <number>      Maximum number of prediction candidates (default: 12)
  --help                Show this help message
`);
}

// Stateful Interactive REPL Shell (CLI-Anything Standard)
function startRepl() {
  printBanner();
  console.log(`${COLORS.dim}Type .help for commands, .mode to switch typing mode, or .exit to quit.\n${COLORS.reset}`);

  let currentMode = 'banglish'; // 'banglish' | 'bangla' | 'english'

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: `${COLORS.green}smartkey${COLORS.reset}[${COLORS.yellow}${currentMode}${COLORS.reset}]> `
  });

  rl.prompt();

  rl.on('line', async (line) => {
    const input = line.trim();
    if (!input) {
      rl.prompt();
      return;
    }

    if (input === '.exit' || input === 'exit' || input === 'quit' || input === '.quit') {
      console.log(`${COLORS.cyan}Goodbye! 👋${COLORS.reset}`);
      process.exit(0);
    }

    if (input === '.help' || input === 'help') {
      printHelp();
      rl.prompt();
      return;
    }

    if (input === '.mode') {
      currentMode = currentMode === 'banglish' ? 'bangla' : (currentMode === 'bangla' ? 'english' : 'banglish');
      rl.setPrompt(`${COLORS.green}smartkey${COLORS.reset}[${COLORS.yellow}${currentMode}${COLORS.reset}]> `);
      console.log(`${COLORS.cyan}Active mode switched to: ${currentMode}${COLORS.reset}`);
      rl.prompt();
      return;
    }

    const parts = input.split(/\s+/);
    const cmd = parts[0].toLowerCase();
    const rest = parts.slice(1);

    if (cmd === 'predict') {
      const res = await handlePredict(rest, { mode: currentMode === 'english' ? 'en' : 'bn', limit: 10 });
      console.log(`${COLORS.yellow}Predictions:${COLORS.reset} ${res.data.predictions.join(' | ')}`);
    } else if (cmd === 'phonetic') {
      const res = await handlePhonetic(rest);
      console.log(`${COLORS.green}Bangla:${COLORS.reset} ${res.data.output}`);
    } else if (cmd === 'command') {
      const res = await handleCommand(rest);
      console.log(`${COLORS.cyan}Detected Intent:${COLORS.reset} ${res.data.intent} -> "${res.data.payload}"`);
    } else if (cmd === 'translate') {
      const res = await handleTranslate(rest, { from: 'bn', to: 'en' });
      outputResult(res, false);
    } else if (cmd === 'clip') {
      const res = await handleClip(rest, { limit: 5 });
      outputResult(res, false);
    } else if (cmd === 'status') {
      const res = handleStatus();
      outputResult(res, false);
    } else {
      // Direct typing in REPL: if in Banglish mode, transliterate and predict
      if (currentMode === 'banglish') {
        const translit = PhoneticEngine.transliterateText(input);
        const preds = predictionEngine.getPredictions(translit, '', true, 8);
        console.log(`${COLORS.green}➔ বাংলা:${COLORS.reset} ${translit}`);
        console.log(`${COLORS.dim}  সাজেশন:${COLORS.reset} ${preds.join('  •  ')}`);
      } else if (currentMode === 'bangla') {
        const preds = predictionEngine.getPredictions(input, '', true, 8);
        console.log(`${COLORS.dim}  সাজেশন:${COLORS.reset} ${preds.join('  •  ')}`);
      } else {
        const preds = predictionEngine.getPredictions(input, '', false, 8);
        console.log(`${COLORS.dim}  Predictions:${COLORS.reset} ${preds.join('  •  ')}`);
      }
    }

    rl.prompt();
  }).on('close', () => {
    console.log(`\n${COLORS.cyan}Session closed.${COLORS.reset}`);
    process.exit(0);
  });
}

// Main CLI Execution Router
async function main() {
  const rawArgs = process.argv.slice(2);

  if (rawArgs.length === 0) {
    // Default to REPL if no command given
    startRepl();
    return;
  }

  const { flags, positionals } = parseFlags(rawArgs);
  const command = (positionals[0] || '').toLowerCase();
  const args = positionals.slice(1);

  if (command === 'help' || rawArgs.includes('--help') || rawArgs.includes('-h')) {
    printHelp();
    process.exit(0);
  }

  let result;
  switch (command) {
    case 'predict':
      result = await handlePredict(args, flags);
      break;
    case 'phonetic':
      result = await handlePhonetic(args, flags);
      break;
    case 'command':
      result = await handleCommand(args, flags);
      break;
    case 'translate':
      result = await handleTranslate(args, flags);
      break;
    case 'clip':
      result = await handleClip(args, flags);
      break;
    case 'status':
      result = handleStatus();
      break;
    case 'repl':
      startRepl();
      return;
    default:
      console.error(`${COLORS.red}Unknown command: "${command}"${COLORS.reset}`);
      printHelp();
      process.exit(1);
  }

  outputResult(result, flags.json);
  process.exit(result && result.success ? 0 : 1);
}

if (require.main === module) {
  main();
}

module.exports = {
  handlePredict,
  handlePhonetic,
  handleCommand,
  handleTranslate,
  handleClip,
  handleStatus,
  parseFlags
};
