/**
 * Smart Voice Keyboard - Desktop UI Controller
 */

document.addEventListener('DOMContentLoaded', async () => {
  // Navigation Tabs
  const navButtons = document.querySelectorAll('.nav-btn');
  const tabContents = document.querySelectorAll('.tab-content');

  navButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetTab = btn.getAttribute('data-tab');
      navButtons.forEach(b => b.classList.remove('active'));
      tabContents.forEach(tc => tc.classList.remove('active'));
      btn.classList.add('active');
      document.getElementById(targetTab)?.classList.add('active');

      if (targetTab === 'tab-clipboard') {
        loadClipboardItems();
      }
    });
  });

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
        voiceResultArea.value += (voiceResultArea.value ? ' ' : '') + final.trim();
      } else if (interim) {
        // Show interim preview
        voicePromptText.textContent = interim;
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
  // TAB 5: Settings Controller
  // -------------------------------------------------------------
  const settingApiKey = document.getElementById('settingApiKey');
  const settingDefaultLanguage = document.getElementById('settingDefaultLanguage');
  const settingSttEngine = document.getElementById('settingSttEngine');
  const settingAutoPaste = document.getElementById('settingAutoPaste');
  const settingAlwaysOnTop = document.getElementById('settingAlwaysOnTop');

  async function loadSettings() {
    if (!window.api) return;
    const s = await window.api.getSettings();
    if (s) {
      if (settingApiKey) settingApiKey.value = s.geminiApiKey || '';
      if (settingDefaultLanguage) settingDefaultLanguage.value = s.defaultLanguage || 'bn';
      if (settingSttEngine) settingSttEngine.value = s.sttEngine || 'native';
      if (settingAutoPaste) settingAutoPaste.checked = s.autoPaste !== false;
      if (settingAlwaysOnTop) settingAlwaysOnTop.checked = s.alwaysOnTop !== false;
    }
  }

  loadSettings();

  document.getElementById('btnTestApiKey')?.addEventListener('click', async () => {
    const key = settingApiKey.value.trim();
    if (!key) {
      alert('অনুগ্রহ করে একটি API Key লিখুন।');
      return;
    }
    const btn = document.getElementById('btnTestApiKey');
    btn.textContent = 'যাচাই হচ্ছে...';
    const res = await window.api.validateApiKey(key);
    btn.textContent = 'টেস্ট করুন';
    if (res.valid) {
      alert('সফল! Gemini API সংযোগ সফল হয়েছে।');
    } else {
      alert('ব্যর্থ: ' + res.error);
    }
  });

  document.getElementById('btnSaveSettings')?.addEventListener('click', async () => {
    const payload = {
      geminiApiKey: settingApiKey.value.trim(),
      defaultLanguage: settingDefaultLanguage.value,
      sttEngine: settingSttEngine.value,
      autoPaste: settingAutoPaste.checked,
      alwaysOnTop: settingAlwaysOnTop.checked
    };
    await window.api.saveSettings(payload);
    alert('সেটিংস সফলভাবে সংরক্ষিত হয়েছে! (Settings saved)');
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
