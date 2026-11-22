/**
 * Smart Voice Keyboard - Desktop UI Controller
 */

document.addEventListener('DOMContentLoaded', async () => {
  // Navigation Tabs & Quick Mode Switcher
  const navButtons = document.querySelectorAll('.nav-btn');
  const tabContents = document.querySelectorAll('.tab-content');

  function switchToTab(targetTab) {
    navButtons.forEach(b => b.classList.remove('active'));
    tabContents.forEach(tc => tc.classList.remove('active'));
    const btn = document.querySelector(`.nav-btn[data-tab="${targetTab}"]`);
    if (btn) btn.classList.add('active');
    const tabEl = document.getElementById(targetTab);
    if (tabEl) tabEl.classList.add('active');

    if (targetTab === 'tab-clipboard') {
      loadClipboardItems();
      document.getElementById('clipboardSearchInput')?.focus();
    } else if (targetTab === 'tab-voice') {
      document.getElementById('voiceResultArea')?.focus();
    } else if (targetTab === 'tab-phonetic') {
      document.getElementById('phoneticInput')?.focus();
    } else if (targetTab === 'tab-english') {
      document.getElementById('englishInput')?.focus();
    } else if (targetTab === 'tab-bangla') {
      document.getElementById('banglaInput')?.focus();
    }
  }

  navButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetTab = btn.getAttribute('data-tab');
      switchToTab(targetTab);
    });
  });

  // Global hotkey-driven mode switching from electron main
  const modeToTabMap = {
    'voice': 'tab-voice',
    'banglish': 'tab-phonetic',
    'english': 'tab-english',
    'bangla': 'tab-bangla',
    'clipboard': 'tab-clipboard',
    'settings': 'tab-settings'
  };

  if (window.api?.onSwitchMode) {
    window.api.onSwitchMode((mode) => {
      const tabId = modeToTabMap[mode] || 'tab-voice';
      switchToTab(tabId);
    });
  }

  // Window Controls
  document.getElementById('btnMinimize')?.addEventListener('click', () => {
    window.api?.minimizeWindow();
  });
  document.getElementById('btnClose')?.addEventListener('click', () => {
    window.api?.hideWindow();
  });

  // -------------------------------------------------------------
  // TAB 1: Speech-to-Text (STT) Controller
  // -------------------------------------------------------------
  let isListening = false;
  let recognition = null;
  let audioContext = null;
  let analyser = null;
  let micStream = null;
  let visualizerAnimId = null;

  const btnToggleVoice = document.getElementById('btnToggleVoice');
  const audioWaveform = document.getElementById('audioWaveform');
  const waveBars = audioWaveform ? audioWaveform.querySelectorAll('.wave-bar') : [];
  const voiceResultArea = document.getElementById('voiceResultArea');
  const voiceLanguageSelect = document.getElementById('voiceLanguageSelect');
  const sttStatusBadge = document.getElementById('sttStatusBadge');
  const voicePromptText = document.getElementById('voicePromptText');

  // Online / Offline Monitor
  function updateNetworkBadge() {
    if (!navigator.onLine) {
      sttStatusBadge.textContent = 'Offline (Local Mode)';
      sttStatusBadge.className = 'text-xs px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-300 font-medium';
    }
  }
  window.addEventListener('online', updateNetworkBadge);
  window.addEventListener('offline', updateNetworkBadge);
  updateNetworkBadge();

  async function startAudioLevelVisualizer() {
    try {
      micStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      audioContext = new AudioCtx();
      analyser = audioContext.createAnalyser();
      analyser.fftSize = 64;
      analyser.smoothingTimeConstant = 0.7;

      const source = audioContext.createMediaStreamSource(micStream);
      source.connect(analyser);

      const dataArray = new Uint8Array(analyser.frequencyBinCount);
      const step = Math.max(1, Math.floor(dataArray.length / waveBars.length));

      const updateBars = () => {
        if (!isListening || !analyser) return;
        analyser.getByteFrequencyData(dataArray);

        for (let i = 0; i < waveBars.length; i++) {
          const val = (dataArray[i * step] || 0) / 255; // 0.0 to 1.0
          const barHeight = Math.max(8, Math.round(8 + val * 36));
          waveBars[i].style.height = `${barHeight}px`;
          if (val > 0.3) {
            waveBars[i].style.backgroundColor = '#10b981';
          } else {
            waveBars[i].style.backgroundColor = '#334155';
          }
        }
        visualizerAnimId = requestAnimationFrame(updateBars);
      };
      updateBars();
    } catch (err) {
      console.warn('Audio Visualizer mic stream fallback:', err);
    }
  }

  function stopAudioLevelVisualizer() {
    if (visualizerAnimId) {
      cancelAnimationFrame(visualizerAnimId);
      visualizerAnimId = null;
    }
    waveBars.forEach(bar => {
      bar.style.height = '8px';
      bar.style.backgroundColor = '#334155';
    });
    if (micStream) {
      micStream.getTracks().forEach(t => t.stop());
      micStream = null;
    }
    if (audioContext && audioContext.state !== 'closed') {
      audioContext.close();
      audioContext = null;
    }
  }

  function applyVoiceAutoPunctuation(rawText, lang) {
    if (!rawText || !rawText.trim()) return '';
    let text = rawText.trim();

    // Spoken punctuation replacement (Unicode & word-boundary aware)
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

    // Contextual sentence ending punctuation
    const lastChar = text.slice(-1);
    const hasPunctuation = ['.', '।', '?', '!', '\n'].includes(lastChar);
    if (!hasPunctuation) {
      if (lang && lang.startsWith('en')) {
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

    return text;
  }

  function initSpeechRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      sttStatusBadge.textContent = 'Speech API Unavailable';
      sttStatusBadge.className = 'text-xs px-2.5 py-0.5 rounded-full bg-red-500/20 text-red-400';
      return;
    }

    recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = voiceLanguageSelect.value;

    recognition.onstart = () => {
      isListening = true;
      startAudioLevelVisualizer();
      btnToggleVoice.classList.remove('bg-emerald-600', 'hover:bg-emerald-500');
      btnToggleVoice.classList.add('bg-red-600', 'hover:bg-red-500', 'animate-pulse');
      sttStatusBadge.textContent = 'Listening...';
      sttStatusBadge.className = 'text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-medium';
      voicePromptText.textContent = 'শুনছি... কথা বলুন (Listening...)';
    };

    recognition.onresult = (event) => {
      let interim = '';
      let final = '';

      for (let i = event.resultIndex; i < event.results.length; ++i) {
        if (event.results[i].isFinal) {
          final += event.results[i][0].transcript;
        } else {
          interim += event.results[i][0].transcript;
        }
      }

      if (final) {
        const processed = applyVoiceAutoPunctuation(final, voiceLanguageSelect?.value || 'bn-BD');
        voiceResultArea.value += (voiceResultArea.value ? ' ' : '') + processed;
        voiceResultArea.scrollTop = voiceResultArea.scrollHeight;
        voicePromptText.textContent = processed;

        // Real-Time Auto-Injection to active app window if autoPaste is enabled
        if (settingAutoPaste?.checked && window.api?.injectTextToActiveWindow) {
          window.api.injectTextToActiveWindow(processed);
        }
      } else if (interim) {
        // Show live real-time interim stream
        voicePromptText.textContent = '🔴 ' + interim;
      }
    };

    recognition.onerror = (event) => {
      console.warn('Speech error:', event.error);
      sttStatusBadge.textContent = 'Error: ' + event.error;
      sttStatusBadge.className = 'text-xs px-2.5 py-0.5 rounded-full bg-red-500/20 text-red-400';
    };

    recognition.onend = () => {
      isListening = false;
      stopAudioLevelVisualizer();
      btnToggleVoice.classList.add('bg-emerald-600', 'hover:bg-emerald-500');
      btnToggleVoice.classList.remove('bg-red-600', 'hover:bg-red-500', 'animate-pulse');
      sttStatusBadge.textContent = navigator.onLine ? 'Standby' : 'Offline Mode';
      sttStatusBadge.className = 'text-xs px-2.5 py-0.5 rounded-full bg-slate-800 text-slate-400';
      voicePromptText.textContent = 'মাইকে চাপ দিয়ে কথা বলুন (Click to speak)';
    };
  }

  initSpeechRecognition();

  voiceLanguageSelect?.addEventListener('change', () => {
    if (recognition) {
      recognition.lang = voiceLanguageSelect.value;
    }
  });

  btnToggleVoice?.addEventListener('click', () => {
    if (!recognition) {
      initSpeechRecognition();
    }
    if (!recognition) return;

    if (isListening) {
      recognition.stop();
    } else {
      try {
        recognition.lang = voiceLanguageSelect.value;
        recognition.start();
      } catch (e) {
        console.warn('Recognition start exception:', e);
      }
    }
  });

  document.getElementById('btnClearVoiceText')?.addEventListener('click', () => {
    voiceResultArea.value = '';
    voiceResultArea.dispatchEvent(new Event('input', { bubbles: true }));
  });

  // -------------------------------------------------------------
  // Phase 6: Continuous Accelerated Hold-to-Delete & Word Boundary
  // -------------------------------------------------------------
  function getWordDeletionCount(textBefore) {
    if (!textBefore || textBefore.length === 0) return 0;
    let index = textBefore.length;
    while (index > 0 && /\s/.test(textBefore[index - 1])) {
      index--;
    }
    if (index === 0) return textBefore.length;
    const isPunct = (ch) => /[\.,!?;:\-–—"'`\(\)\[\]\{\}।॥]/.test(ch);
    if (isPunct(textBefore[index - 1])) {
      while (index > 0 && isPunct(textBefore[index - 1])) {
        index--;
      }
      return textBefore.length - index;
    }
    while (index > 0 && !/\s/.test(textBefore[index - 1]) && !isPunct(textBefore[index - 1])) {
      index--;
    }
    return textBefore.length - index;
  }

  function deletePreviousWord(inputElement) {
    if (!inputElement) return;
    const start = inputElement.selectionStart;
    const end = inputElement.selectionEnd;
    if (start !== end) {
      inputElement.setRangeText('', start, end, 'end');
      inputElement.dispatchEvent(new Event('input', { bubbles: true }));
      return;
    }
    const val = inputElement.value;
    const textBefore = val.slice(0, start);
    const count = getWordDeletionCount(textBefore);
    if (count > 0) {
      inputElement.setRangeText('', start - count, start, 'end');
      inputElement.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  function attachHoldToDelete(button, inputElement) {
    if (!button || !inputElement) return;
    let holdTimer = null;
    let repeatTimer = null;
    let startTime = 0;

    const stop = () => {
      clearTimeout(holdTimer);
      clearInterval(repeatTimer);
      holdTimer = null;
      repeatTimer = null;
    };

    button.addEventListener('mousedown', (e) => {
      e.preventDefault();
      startTime = Date.now();
      deletePreviousWord(inputElement);

      holdTimer = setTimeout(() => {
        repeatTimer = setInterval(() => {
          deletePreviousWord(inputElement);
        }, 110);
      }, 350);
    });

    button.addEventListener('mouseup', stop);
    button.addEventListener('mouseleave', stop);
    button.addEventListener('touchend', stop);
    button.addEventListener('touchcancel', stop);

    button.addEventListener('touchstart', (e) => {
      e.preventDefault();
      startTime = Date.now();
      deletePreviousWord(inputElement);

      holdTimer = setTimeout(() => {
        repeatTimer = setInterval(() => {
          deletePreviousWord(inputElement);
        }, 110);
      }, 350);
    });
  }

  // Intercept Ctrl+Backspace or Alt+Backspace on inputs for intelligent Unicode word deletion
  [document.getElementById('voiceResultArea'), document.getElementById('translateInput'), document.getElementById('phoneticInput')].forEach(input => {
    if (!input) return;
    input.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.altKey) && e.key === 'Backspace') {
        e.preventDefault();
        deletePreviousWord(input);
      }
    });
  });

  // Attach to UI buttons
  attachHoldToDelete(document.getElementById('btnDeleteWordVoiceText'), document.getElementById('voiceResultArea'));
  attachHoldToDelete(document.getElementById('btnDelWordTranslate'), document.getElementById('translateInput'));
  attachHoldToDelete(document.getElementById('btnDelWordPhonetic'), document.getElementById('phoneticInput'));

  document.getElementById('btnClearTranslate')?.addEventListener('click', () => {
    const el = document.getElementById('translateInput');
    if (el) {
      el.value = '';
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }
  });

  document.getElementById('btnClearPhonetic')?.addEventListener('click', () => {
    const el = document.getElementById('phoneticInput');
    const out = document.getElementById('phoneticOutput');
    if (el) {
      el.value = '';
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }
    if (out) out.textContent = '';
  });

  document.getElementById('btnCopyVoiceText')?.addEventListener('click', () => {
    if (voiceResultArea.value) {
      navigator.clipboard.writeText(voiceResultArea.value);
    }
  });

  document.getElementById('btnInjectVoiceToApp')?.addEventListener('click', async () => {
    const text = voiceResultArea.value.trim();
    if (text && window.api) {
      await window.api.injectTextToActiveWindow(text);
    }
  });

  document.getElementById('btnAIProcessCommand')?.addEventListener('click', async () => {
    const text = voiceResultArea.value.trim();
    if (!text || !window.api) return;

    const originalBtnText = document.getElementById('btnAIProcessCommand').innerHTML;
    document.getElementById('btnAIProcessCommand').innerHTML = '<span>⏳ প্রসেস হচ্ছে...</span>';

    try {
      const res = await window.api.executeVoiceCommand(text);
      if (res && res.result) {
        voiceResultArea.value = res.result;
      }
    } catch (err) {
      alert('AI Command Error: ' + err.message);
    } finally {
      document.getElementById('btnAIProcessCommand').innerHTML = originalBtnText;
    }
  });

  // Smart AI Action Pills
  document.querySelectorAll('.smart-ai-pill').forEach(pill => {
    pill.addEventListener('click', async () => {
      const actionType = pill.getAttribute('data-action');
      const text = voiceResultArea.value.trim();
      if (!text || !window.api) {
        alert('অনুগ্রহ করে আগে কিছু টেক্সট লিখুন বা কথা বলুন।');
        return;
      }

      const originalHtml = pill.innerHTML;
      pill.innerHTML = '⏳ ...';
      try {
        const res = await window.api.executeSmartAction(text, actionType);
        if (res && res.result) {
          voiceResultArea.value = res.result;
        }
      } catch (err) {
        alert('AI ত্রুটি: ' + err.message);
      } finally {
        pill.innerHTML = originalHtml;
      }
    });
  });

  // -------------------------------------------------------------
  // TAB 2: Live Translation (Gemini)
  // -------------------------------------------------------------
  let sourceLang = 'en';
  let targetLang = 'bn';
  const labelSourceLang = document.getElementById('labelSourceLang');
  const labelTargetLang = document.getElementById('labelTargetLang');
  const translateInput = document.getElementById('translateInput');
  const translateOutput = document.getElementById('translateOutput');

  document.getElementById('btnSwapTranslateLangs')?.addEventListener('click', () => {
    const temp = sourceLang;
    sourceLang = targetLang;
    targetLang = temp;

    labelSourceLang.textContent = sourceLang === 'en' ? 'English' : 'বাংলা (Bengali)';
    labelTargetLang.textContent = targetLang === 'en' ? 'English' : 'বাংলা (Bengali)';

    const tempText = translateInput.value;
    translateInput.value = translateOutput.value;
    translateOutput.value = tempText;
  });

  // Gboard-style live inline translation with 450ms debounce
  let translateDebounceTimer = null;
  translateInput?.addEventListener('input', () => {
    clearTimeout(translateDebounceTimer);
    const text = translateInput.value.trim();
    if (!text) {
      translateOutput.value = '';
      return;
    }
    translateOutput.value = 'অনুবাদ হচ্ছে (Translating live)...';
    translateDebounceTimer = setTimeout(async () => {
      try {
        const result = await window.api.translate(text, sourceLang, targetLang);
        translateOutput.value = result;
      } catch (err) {
        translateOutput.value = 'Error: ' + err.message;
      }
    }, 450);
  });

  translateInput?.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const text = translateOutput.value.trim();
      if (text && window.api) {
        await window.api.injectTextToActiveWindow(text);
      }
    }
  });

  document.getElementById('btnExecuteTranslate')?.addEventListener('click', async () => {
    const text = translateInput.value.trim();
    if (!text || !window.api) return;

    translateOutput.value = 'অনুবাদ করা হচ্ছে (Translating with Gemini)...';
    try {
      const result = await window.api.translate(text, sourceLang, targetLang);
      translateOutput.value = result;
    } catch (err) {
      translateOutput.value = 'Error: ' + err.message;
    }
  });

  document.getElementById('btnInjectTranslationToApp')?.addEventListener('click', async () => {
    const text = translateOutput.value.trim();
    if (text && window.api) {
      await window.api.injectTextToActiveWindow(text);
    }
  });

  // -------------------------------------------------------------
  // TAB 3: Phonetic (Banglish to Bangla) with Smart Prediction
  // -------------------------------------------------------------
  const phoneticInput = document.getElementById('phoneticInput');
  const phoneticOutput = document.getElementById('phoneticOutput');
  const desktopPredictionChips = document.getElementById('desktopPredictionChips');

  const banglaPredictions = {
    'আমি': ['ভালো', 'আছি', 'তোমাকে', 'এখন', 'যাবো', 'বলছি'],
    'তুমি': ['কেমন', 'কোথায়', 'কি', 'আছো', 'যাবে', 'করছো'],
    'কেমন': ['আছো', 'আছেন', 'হলো', 'চলছে'],
    'ধন্যবাদ': ['ভাই', 'আপনাকে', 'অনেক', 'প্রিয়'],
    'শুভ': ['সকাল', 'রাত্রি', 'কামনা', 'দিন'],
    'আসসালামু': ['আলাইকুম'],
    'ইনশা': ['আল্লাহ'],
    'আলহামদুলিল্লাহ': ['ভালো', 'আছি']
  };

  function updateDesktopPredictionChips(currentText) {
    if (!desktopPredictionChips) return;
    const tokens = currentText.trim().split(/\s+/).filter(Boolean);
    if (!tokens.length) {
      desktopPredictionChips.innerHTML = '';
      return;
    }
    const lastWord = tokens[tokens.length - 1];
    const suggestions = banglaPredictions[lastWord] || [];

    if (!suggestions.length) {
      desktopPredictionChips.innerHTML = '';
      return;
    }

    desktopPredictionChips.innerHTML = suggestions.map(s => `
      <button class="pred-chip px-2.5 py-0.5 rounded-full bg-slate-800 hover:bg-emerald-600 text-slate-300 hover:text-white text-xs border border-slate-700/60 transition cursor-pointer">
        ${s}
      </button>
    `).join('');

    desktopPredictionChips.querySelectorAll('.pred-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const word = chip.textContent.trim();
        phoneticOutput.textContent = (phoneticOutput.textContent.trim() + ' ' + word).trim();
        updateDesktopPredictionChips(word);
      });
    });
  }

  phoneticInput?.addEventListener('input', async () => {
    const text = phoneticInput.value;
    if (!text) {
      phoneticOutput.textContent = '';
      if (desktopPredictionChips) desktopPredictionChips.innerHTML = '';
      return;
    }
    if (window.api) {
      const converted = await window.api.transliterateText(text);
      phoneticOutput.textContent = converted;
      updateDesktopPredictionChips(converted);
    }
  });

  phoneticInput?.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const text = phoneticOutput.textContent.trim();
      if (text && window.api) {
        await window.api.injectTextToActiveWindow(text);
      }
    }
  });

  document.getElementById('btnCopyPhonetic')?.addEventListener('click', () => {
    if (phoneticOutput.textContent) {
      navigator.clipboard.writeText(phoneticOutput.textContent);
    }
  });

  document.getElementById('btnInjectPhoneticToApp')?.addEventListener('click', async () => {
    const text = phoneticOutput.textContent.trim();
    if (text && window.api) {
      await window.api.injectTextToActiveWindow(text);
    }
  });

  // -------------------------------------------------------------
  // TAB 3B & 3C: English & Pure Bangla Direct Typist (Phase 8)
  // -------------------------------------------------------------
  const englishInput = document.getElementById('englishInput');
  const banglaInput = document.getElementById('banglaInput');

  const DIRECT_BANGLA_MAP = {
    'k': 'ক', 'K': 'খ', 'g': 'গ', 'G': 'ঘ', 'N': 'ঙ',
    'c': 'চ', 'C': 'ছ', 'j': 'জ', 'J': 'ঝ',
    't': 'ট', 'T': 'ঠ', 'd': 'ড', 'D': 'ঢ',
    'r': 'র', 'R': 'ড়', 'l': 'ল', 'L': 'ল',
    'p': 'প', 'P': 'ফ', 'b': 'ব', 'B': 'ভ', 'm': 'ম',
    's': 'স', 'S': 'শ', 'h': 'হ', 'H': 'ঃ',
    'a': 'া', 'A': 'আ', 'i': 'ি', 'I': 'ী',
    'u': 'ু', 'U': 'ূ', 'e': 'ে', 'E': 'ৈ', 'o': 'ো', 'O': 'ৌ'
  };

  // English Mode
  englishInput?.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const text = englishInput.value.trim();
      if (text && window.api) {
        await window.api.injectTextToActiveWindow(text);
      }
    }
  });

  document.getElementById('btnInjectEnglishToApp')?.addEventListener('click', async () => {
    const text = englishInput?.value.trim();
    if (text && window.api) {
      await window.api.injectTextToActiveWindow(text);
    }
  });

  document.getElementById('btnCopyEnglish')?.addEventListener('click', () => {
    if (englishInput?.value) {
      navigator.clipboard.writeText(englishInput.value);
    }
  });

  document.getElementById('btnClearEnglish')?.addEventListener('click', () => {
    if (englishInput) {
      englishInput.value = '';
      englishInput.dispatchEvent(new Event('input', { bubbles: true }));
    }
  });

  // Pure Bangla Mode
  banglaInput?.addEventListener('input', () => {
    const val = banglaInput.value;
    let out = '';
    for (let i = 0; i < val.length; i++) {
      const ch = val[i];
      out += DIRECT_BANGLA_MAP[ch] || ch;
    }
    if (out !== val) {
      const start = banglaInput.selectionStart;
      banglaInput.value = out;
      banglaInput.setSelectionRange(start, start);
    }
  });

  banglaInput?.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const text = banglaInput.value.trim();
      if (text && window.api) {
        await window.api.injectTextToActiveWindow(text);
      }
    }
  });

  document.getElementById('btnInjectBanglaToApp')?.addEventListener('click', async () => {
    const text = banglaInput?.value.trim();
    if (text && window.api) {
      await window.api.injectTextToActiveWindow(text);
    }
  });

  document.getElementById('btnCopyBangla')?.addEventListener('click', () => {
    if (banglaInput?.value) {
      navigator.clipboard.writeText(banglaInput.value);
    }
  });

  document.getElementById('btnClearBangla')?.addEventListener('click', () => {
    if (banglaInput) {
      banglaInput.value = '';
      banglaInput.dispatchEvent(new Event('input', { bubbles: true }));
    }
  });

  attachHoldToDelete(document.getElementById('btnDelWordEnglish'), englishInput);
  attachHoldToDelete(document.getElementById('btnDelWordBangla'), banglaInput);

  // -------------------------------------------------------------
  // TAB 4: Unlimited Clipboard Manager
  // -------------------------------------------------------------
  const clipboardList = document.getElementById('clipboardList');
  const clipboardSearchInput = document.getElementById('clipboardSearchInput');
  let filterPinnedOnly = false;

  async function loadClipboardItems() {
    if (!window.api || !clipboardList) return;
    const query = clipboardSearchInput.value.trim();
    const items = await window.api.getClipboardItems(query, filterPinnedOnly);

    if (!items || items.length === 0) {
      clipboardList.innerHTML = `
        <div class="text-center py-8 text-slate-500 text-xs">
          কোনো ক্লিপ পাওয়া যায়নি (No clips found)
        </div>`;
      return;
    }

    clipboardList.innerHTML = items.map(item => `
      <div class="clip-card ${item.is_pinned ? 'pinned' : ''}" data-id="${item.id}">
        <div class="flex justify-between items-start gap-2">
          <p class="text-xs text-slate-200 break-words flex-1 leading-relaxed">${escapeHtml(item.text)}</p>
          <div class="flex items-center gap-1">
            <button class="btn-pin p-1 text-xs hover:text-emerald-400 ${item.is_pinned ? 'text-emerald-400' : 'text-slate-500'}" title="Pin clip">
              ${item.is_pinned ? '★' : '☆'}
            </button>
            <button class="btn-del p-1 text-xs text-slate-500 hover:text-red-400" title="Delete">
              ✕
            </button>
          </div>
        </div>
        <div class="flex justify-between items-center text-[10px] text-slate-500 pt-1 border-t border-slate-800/60">
          <span>${formatTime(item.created_at)} • ${item.char_count} chars • copied ${item.copy_count || 1}x</span>
          <button class="btn-paste-clip text-emerald-400 hover:underline">
            পেস্ট করুন ➔
          </button>
        </div>
      </div>
    `).join('');

    // Attach event listeners to clips
    clipboardList.querySelectorAll('.clip-card').forEach(card => {
      const id = card.getAttribute('data-id');
      card.querySelector('.btn-pin')?.addEventListener('click', async () => {
        await window.api.togglePinClip(id);
        loadClipboardItems();
      });
      card.querySelector('.btn-del')?.addEventListener('click', async () => {
        await window.api.deleteClip(id);
        loadClipboardItems();
      });
      card.querySelector('.btn-paste-clip')?.addEventListener('click', async () => {
        const itemText = card.querySelector('p').textContent;
        await window.api.injectTextToActiveWindow(itemText);
      });
    });
  }

  clipboardSearchInput?.addEventListener('input', () => {
    loadClipboardItems();
  });

  document.getElementById('btnFilterPinned')?.addEventListener('click', (e) => {
    filterPinnedOnly = !filterPinnedOnly;
    e.target.classList.toggle('bg-emerald-600', filterPinnedOnly);
    e.target.classList.toggle('text-white', filterPinnedOnly);
    loadClipboardItems();
  });

  document.getElementById('btnClearClipboardHistory')?.addEventListener('click', async () => {
    if (confirm('আপনি কি সমস্ত অপিনকৃত ক্লিপ মুছে ফেলতে চান? (Clear unpinned history?)')) {
      await window.api.clearClipboard(true);
      loadClipboardItems();
    }
  });

  // Cloud Sync Handler
  const btnTriggerCloudSync = document.getElementById('btnTriggerCloudSync');
  const syncStatusLabel = document.getElementById('syncStatusLabel');
  const syncDotIndicator = document.getElementById('syncDotIndicator');

  async function updateSyncStatus() {
    if (!window.api || !syncStatusLabel) return;
    try {
      const info = await window.api.getSyncStatus();
      if (info) {
        if (info.status === 'syncing') {
          syncStatusLabel.textContent = 'ক্লাউড সিঙ্ক: সিঙ্ক হচ্ছে...';
          if (syncDotIndicator) syncDotIndicator.className = 'w-2 h-2 rounded-full bg-amber-400 animate-ping';
        } else if (info.status === 'failed') {
          syncStatusLabel.textContent = `ক্লাউড সিঙ্ক: লোকাল কিউ (${info.pendingCount})`;
          if (syncDotIndicator) syncDotIndicator.className = 'w-2 h-2 rounded-full bg-slate-500';
        } else {
          syncStatusLabel.textContent = info.pendingCount > 0 
            ? `ক্লাউড সিঙ্ক: পেন্ডিং (${info.pendingCount})` 
            : 'ক্লাউড সিঙ্ক: প্রস্তুত (Synced)';
          if (syncDotIndicator) syncDotIndicator.className = 'w-2 h-2 rounded-full bg-emerald-400';
        }
      }
    } catch (e) {}
  }

  btnTriggerCloudSync?.addEventListener('click', async () => {
    if (!window.api) return;
    btnTriggerCloudSync.textContent = '🔄 সিঙ্ক হচ্ছে...';
    try {
      await window.api.triggerSync();
      await updateSyncStatus();
      loadClipboardItems();
    } catch (e) {
      alert('Sync Error: ' + e.message);
    } finally {
      btnTriggerCloudSync.textContent = '🔄 সিঙ্ক করুন';
    }
  });

  // -------------------------------------------------------------
  // TAB 5: Settings Controller & Dynamic Theme Switcher
  // -------------------------------------------------------------
  const settingAppTheme = document.getElementById('settingAppTheme');
  const settingApiKey = document.getElementById('settingApiKey');
  const settingDefaultLanguage = document.getElementById('settingDefaultLanguage');
  const settingSttEngine = document.getElementById('settingSttEngine');
  const settingAutoPaste = document.getElementById('settingAutoPaste');
  const settingAlwaysOnTop = document.getElementById('settingAlwaysOnTop');

  function applyTheme(themeKey) {
    const validThemes = ['dark', 'amoled', 'light', 'cyber', 'ocean', 'forest', 'sunset'];
    const activeTheme = validThemes.includes(themeKey) ? themeKey : 'dark';
    document.body.className = `theme-${activeTheme} select-none`;
    if (settingAppTheme) settingAppTheme.value = activeTheme;
  }

  settingAppTheme?.addEventListener('change', (e) => {
    const newTheme = e.target.value;
    applyTheme(newTheme);
    const currentPayload = {
      geminiApiKey: settingApiKey?.value?.trim() || '',
      defaultLanguage: settingDefaultLanguage?.value || 'bn',
      sttEngine: settingSttEngine?.value || 'native',
      theme: newTheme,
      autoPaste: settingAutoPaste ? settingAutoPaste.checked : true,
      alwaysOnTop: settingAlwaysOnTop ? settingAlwaysOnTop.checked : true
    };
    if (window.api?.saveSettings) {
      window.api.saveSettings(currentPayload);
    }
    localStorage.setItem('smart_keyboard_theme', newTheme);
    localStorage.setItem('smart_keyboard_settings', JSON.stringify(currentPayload));
  });

  async function loadSettings() {
    let s = null;
    if (window.api?.getSettings) {
      s = await window.api.getSettings();
    } else {
      try {
        const local = localStorage.getItem('smart_keyboard_settings');
        if (local) s = JSON.parse(local);
      } catch (e) {}
    }

    const savedTheme = s?.theme || localStorage.getItem('smart_keyboard_theme') || 'dark';
    applyTheme(savedTheme);

    if (s) {
      if (settingApiKey) settingApiKey.value = s.geminiApiKey || '';
      if (settingDefaultLanguage) settingDefaultLanguage.value = s.defaultLanguage || 'bn';
      if (settingSttEngine) settingSttEngine.value = s.sttEngine || 'native';
      if (settingAutoPaste) settingAutoPaste.checked = s.autoPaste !== false;
      if (settingAlwaysOnTop) settingAlwaysOnTop.checked = s.alwaysOnTop !== false;
    }
    pingGeminiStatus();
  }

  loadSettings();

  async function pingGeminiStatus(apiKeyInput = null) {
    const key = apiKeyInput !== null ? apiKeyInput : (settingApiKey?.value?.trim() || '');
    const statusLed = document.getElementById('apiStatusLed');
    const statusText = document.getElementById('apiKeyStatus');
    const latencyText = document.getElementById('apiLatencyText');
    const headerBadge = document.getElementById('headerGeminiBadge');

    if (!key) {
      if (statusLed) statusLed.className = 'w-2.5 h-2.5 rounded-full bg-slate-500';
      if (statusText) statusText.textContent = '⚪ API Key খালি (অনুবাদ নিষ্ক্রিয়)';
      if (latencyText) latencyText.textContent = '--';
      if (headerBadge) {
        headerBadge.textContent = '⚪ Gemini Inactive';
        headerBadge.className = 'text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-mono cursor-pointer';
      }
      return { connected: false };
    }

    if (statusLed) statusLed.className = 'w-2.5 h-2.5 rounded-full bg-amber-500 animate-ping';
    if (statusText) statusText.textContent = '🟡 কানেকশন যাচাই হচ্ছে...';

    const start = Date.now();
    try {
      let isValid = false;
      let latency = 0;
      if (window.api?.validateApiKey) {
        const res = await window.api.validateApiKey(key);
        isValid = !!res.valid;
        latency = Math.max(1, Date.now() - start);
      } else {
        const res = await fetch('/api/gemini/ping', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ apiKey: key })
        });
        const data = await res.json();
        isValid = !!data?.data?.connected;
        latency = data?.data?.latency_ms || Math.max(1, Date.now() - start);
      }

      if (isValid) {
        if (statusLed) statusLed.className = 'w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-sm shadow-emerald-500/50';
        if (statusText) statusText.textContent = '🟢 Gemini 1.5 Flash সক্রিয় ও প্রস্তুত (Active)';
        if (latencyText) latencyText.textContent = `${latency}ms`;
        if (headerBadge) {
          headerBadge.textContent = `🟢 Gemini (${latency}ms)`;
          headerBadge.className = 'text-xs px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-mono cursor-pointer transition hover:bg-emerald-500/30';
        }
        return { connected: true, latency };
      } else {
        throw new Error('API Key কাজ করছে না');
      }
    } catch (err) {
      const latency = Math.max(1, Date.now() - start);
      if (statusLed) statusLed.className = 'w-2.5 h-2.5 rounded-full bg-red-500';
      if (statusText) statusText.textContent = '🔴 কানেকশন ত্রুটি: ' + (err.message || 'ভুল কী');
      if (latencyText) latencyText.textContent = `${latency}ms`;
      if (headerBadge) {
        headerBadge.textContent = '🔴 Gemini Error';
        headerBadge.className = 'text-xs px-2 py-0.5 rounded-full bg-red-500/20 text-red-400 font-mono cursor-pointer';
      }
      return { connected: false, error: err.message };
    }
  }

  document.getElementById('headerGeminiBadge')?.addEventListener('click', () => {
    pingGeminiStatus();
  });

  document.getElementById('btnTestApiKey')?.addEventListener('click', async () => {
    const key = settingApiKey.value.trim();
    if (!key) {
      alert('অনুগ্রহ করে একটি API Key লিখুন।');
      return;
    }
    const btn = document.getElementById('btnTestApiKey');
    btn.textContent = 'যাচাই হচ্ছে...';
    const status = await pingGeminiStatus(key);
    btn.textContent = 'টেস্ট করুন';
    if (status.connected) {
      alert(`সফল! Gemini API সংযোগ সক্রিয়। 🟢 (ল্যাটেন্সি: ${status.latency}ms)`);
    } else {
      alert('ব্যর্থ: ' + (status.error || 'ভুল বা নিষ্ক্রিয় API Key'));
    }
  });

  document.getElementById('btnSaveSettings')?.addEventListener('click', async () => {
    const payload = {
      geminiApiKey: settingApiKey.value.trim(),
      defaultLanguage: settingDefaultLanguage.value,
      sttEngine: settingSttEngine.value,
      theme: settingAppTheme ? settingAppTheme.value : 'dark',
      autoPaste: settingAutoPaste.checked,
      alwaysOnTop: settingAlwaysOnTop.checked
    };
    if (window.api?.saveSettings) {
      await window.api.saveSettings(payload);
    }
    localStorage.setItem('smart_keyboard_settings', JSON.stringify(payload));
    localStorage.setItem('smart_keyboard_theme', payload.theme);
    alert('সেটিংস সফলভাবে সংরক্ষিত হয়েছে! (Settings saved 🟢)');
  });

  // Backup & Restore Handlers (Phase 5)
  document.getElementById('btnExportBackup')?.addEventListener('click', async () => {
    try {
      const clips = window.api?.getClips ? await window.api.getClips() : (JSON.parse(localStorage.getItem('smart_keyboard_clips') || '[]'));
      const settingsPayload = {
        geminiApiKey: settingApiKey?.value?.trim() || '',
        defaultLanguage: settingDefaultLanguage?.value || 'bn',
        sttEngine: settingSttEngine?.value || 'native',
        theme: settingAppTheme ? settingAppTheme.value : 'dark',
        autoPaste: settingAutoPaste ? settingAutoPaste.checked : true,
        alwaysOnTop: settingAlwaysOnTop ? settingAlwaysOnTop.checked : true
      };

      const backupData = {
        app: 'smart-voices-keyboard',
        version: '1.7.0',
        exported_at: new Date().toISOString(),
        encrypted: false,
        data: {
          settings: settingsPayload,
          clips: clips,
          user_dictionary: []
        }
      };

      const jsonBlob = new Blob([JSON.stringify(backupData, null, 2)], { type: 'application/json' });
      const downloadUrl = URL.createObjectURL(jsonBlob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = `smart-voices-backup-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(downloadUrl);
      alert('ব্যাকআপ ফাইল সফলভাবে ডাউনলোড হয়েছে! 📥');
    } catch (e) {
      alert('ব্যাকআপ তৈরি ব্যর্থ: ' + e.message);
    }
  });

  const backupFileInput = document.getElementById('backupFileInput');
  document.getElementById('btnTriggerImportBackup')?.addEventListener('click', () => {
    backupFileInput?.click();
  });

  backupFileInput?.addEventListener('change', (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (e) => {
      try {
        const rawJson = e.target.result;
        const backupObj = JSON.parse(rawJson);
        if (backupObj.app !== 'smart-voices-keyboard') {
          alert('ত্রুটি: এটি স্মার্ট ভয়েস কিবোর্ডের বৈধ ব্যাকআপ ফাইল নয়।');
          return;
        }

        const data = backupObj.data || {};
        if (data.settings) {
          const s = data.settings;
          if (settingApiKey && s.geminiApiKey) settingApiKey.value = s.geminiApiKey;
          if (settingDefaultLanguage && s.defaultLanguage) settingDefaultLanguage.value = s.defaultLanguage;
          if (settingSttEngine && s.sttEngine) settingSttEngine.value = s.sttEngine;
          if (s.theme) {
            applyTheme(s.theme);
          }
          if (settingAutoPaste && s.autoPaste !== undefined) settingAutoPaste.checked = s.autoPaste;
          if (settingAlwaysOnTop && s.alwaysOnTop !== undefined) settingAlwaysOnTop.checked = s.alwaysOnTop;
        }

        let importedClips = 0;
        if (Array.isArray(data.clips)) {
          for (const c of data.clips) {
            if (c.text) {
              if (window.api?.saveClip) {
                await window.api.saveClip(c.text);
              }
              importedClips++;
            }
          }
        }

        alert(`রিস্টোর সম্পন্ন! ${importedClips} টি ক্লিপ ও সেটিংস সফলভাবে লোড হয়েছে। 🟢`);
        loadClipboardItems();
      } catch (err) {
        alert('রিস্টোর ত্রুটি: ' + err.message);
      } finally {
        backupFileInput.value = '';
      }
    };
    reader.readAsText(file);
  });

  // Helpers
  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function formatTime(timestamp) {
    if (!timestamp) return '';
    const diff = Math.floor((Date.now() - timestamp) / 1000);
    if (diff < 60) return `${diff}s ago`;
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return new Date(timestamp).toLocaleDateString();
  }
});
