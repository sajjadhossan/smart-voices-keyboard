const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  // Gemini AI Translation & Commands
  translate: (text, sourceLang, targetLang) => 
    ipcRenderer.invoke('gemini:translate', { text, sourceLang, targetLang }),
  
  executeVoiceCommand: (voiceInput) => 
    ipcRenderer.invoke('gemini:voice-command', { voiceInput }),

  executeSmartAction: (text, actionType) => 
    ipcRenderer.invoke('gemini:smart-action', { text, actionType }),
  
  validateApiKey: (key) => 
    ipcRenderer.invoke('gemini:validate-key', { key }),

  // Phonetic Engine (Banglish to Bangla)
  transliterateText: (text) => 
    ipcRenderer.invoke('phonetic:transliterate', { text }),

  // Clipboard Manager
  getClipboardItems: (query, filterPinned) => 
    ipcRenderer.invoke('clipboard:get-all', { query, filterPinned }),
  
  addClip: (text, source) => 
    ipcRenderer.invoke('clipboard:add', { text, source }),

  togglePinClip: (id) => 
    ipcRenderer.invoke('clipboard:toggle-pin', { id }),

  deleteClip: (id) => 
    ipcRenderer.invoke('clipboard:delete', { id }),

  clearClipboard: (keepPinned) => 
    ipcRenderer.invoke('clipboard:clear', { keepPinned }),

  // Inject text to active window (Windows / macOS)
  injectTextToActiveWindow: (text) => 
    ipcRenderer.invoke('desktop:inject-text', { text }),

  // Cross-Device Sync
  triggerSync: () => 
    ipcRenderer.invoke('sync:trigger'),
  
  getSyncStatus: () => 
    ipcRenderer.invoke('sync:get-status'),

  // Settings
  getSettings: () => 
    ipcRenderer.invoke('settings:get'),

  saveSettings: (settings) => 
    ipcRenderer.invoke('settings:save', { settings }),

  // Window control
  hideWindow: () => 
    ipcRenderer.invoke('window:hide'),

  minimizeWindow: () => 
    ipcRenderer.invoke('window:minimize'),

  // Desktop Mode Switching Events (Phase 8)
  onSwitchMode: (callback) => {
    ipcRenderer.on('switch:mode', (_e, mode) => callback(mode));
  }
});
