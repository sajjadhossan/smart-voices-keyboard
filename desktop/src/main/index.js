/**
 * Smart Voice Keyboard - Desktop Main Process (Windows & macOS)
 * Manages floating window, system tray, global hotkeys, clipboard listener, and text injection.
 */

const { app, BrowserWindow, Tray, Menu, globalShortcut, ipcMain, clipboard } = require('electron');
const path = require('path');
const fs = require('fs');
const { exec } = require('child_process');
require('dotenv').config();

const { GeminiTranslator } = require('../../core/gemini/translator');
const { VoiceCommandEngine } = require('../../core/gemini/voice-commands');
const { PhoneticEngine } = require('../../core/phonetic/phonetic-engine');
const { ClipboardStore } = require('../../core/clipboard/clipboard-store');

let mainWindow = null;
let tray = null;
const isMac = process.platform === 'darwin';

// Initialize core services
const userDataPath = app.getPath('userData');
const clipboardStore = new ClipboardStore(path.join(userDataPath, 'data'));
const settingsFile = path.join(userDataPath, 'settings.json');

// Default user settings
let appSettings = {
  geminiApiKey: process.env.GEMINI_API_KEY || '',
  sttEngine: 'native',
  defaultLanguage: 'bn',
  autoPaste: true,
  alwaysOnTop: true,
  hotkeyPopup: isMac ? 'Command+Alt+Space' : 'Control+Alt+Space',
  hotkeyClipboard: isMac ? 'Command+Alt+V' : 'Control+Alt+V'
};

// Load saved settings if exist
try {
  if (fs.existsSync(settingsFile)) {
    appSettings = { ...appSettings, ...JSON.parse(fs.readFileSync(settingsFile, 'utf8')) };
  }
} catch (e) {
  console.error('Error loading settings:', e);
}

const geminiTranslator = new GeminiTranslator(appSettings.geminiApiKey);
const voiceCommandEngine = new VoiceCommandEngine(appSettings.geminiApiKey);

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 680,
    height: 520,
    minWidth: 500,
    minHeight: 400,
    frame: false,
    transparent: false,
    alwaysOnTop: appSettings.alwaysOnTop,
    skipTaskbar: false,
    resizable: true,
    show: false,
    backgroundColor: '#0f172a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  mainWindow.on('close', (event) => {
    if (!app.isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
    return false;
  });
}

function createTray() {
  // Use a fallback or create system tray
  tray = new Tray(path.join(__dirname, '../assets/tray-icon.png'));
  const contextMenu = Menu.buildFromTemplate([
    {
      label: 'Open Smart Voice Keyboard',
      click: () => {
        mainWindow.show();
        mainWindow.focus();
      }
    },
    {
      label: 'Dictate / Translate (Shortcut)',
      click: () => {
        mainWindow.show();
        mainWindow.webContents.send('route:action', 'dictate');
      }
    },
    {
      label: 'Clipboard Manager',
      click: () => {
        mainWindow.show();
        mainWindow.webContents.send('route:action', 'clipboard');
      }
    },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        app.isQuitting = true;
        app.quit();
      }
    }
  ]);

  tray.setToolTip('Smart Voice Keyboard by Sajjad Hossan');
  tray.setContextMenu(contextMenu);
  tray.on('click', () => {
    if (mainWindow.isVisible()) {
      mainWindow.hide();
    } else {
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

function registerGlobalHotkeys() {
  globalShortcut.unregisterAll();

  // Toggle main popup
  if (appSettings.hotkeyPopup) {
    try {
      globalShortcut.register(appSettings.hotkeyPopup, () => {
        if (mainWindow.isVisible()) {
          mainWindow.hide();
        } else {
          mainWindow.show();
          mainWindow.focus();
        }
      });
    } catch (e) {
      console.warn('Failed to register popup hotkey:', e);
    }
  }

  // Toggle clipboard
  if (appSettings.hotkeyClipboard) {
    try {
      globalShortcut.register(appSettings.hotkeyClipboard, () => {
        mainWindow.show();
        mainWindow.focus();
        mainWindow.webContents.send('route:action', 'clipboard');
      });
    } catch (e) {
      console.warn('Failed to register clipboard hotkey:', e);
    }
  }
}

// Background Clipboard Watcher
let lastCopiedText = '';
function startClipboardWatcher() {
  setInterval(() => {
    try {
      const currentText = clipboard.readText();
      if (currentText && currentText.trim() && currentText !== lastCopiedText) {
        lastCopiedText = currentText;
        clipboardStore.addClip(currentText, 'desktop');
      }
    } catch (err) {
      // Ignore clipboard read errors
    }
  }, 1000);
}

// Text injection into active window
function injectTextToActiveWindow(textToInject) {
  if (!textToInject) return;

  // Save current clipboard content
  const previousClip = clipboard.readText();
  
  // Set injected text to clipboard
  clipboard.writeText(textToInject);

  // Hide the popup so focus returns to user's active window (e.g., Word, Chrome, Notepad)
  mainWindow.hide();

  // Small delay to let focus restore, then simulate paste
  setTimeout(() => {
    if (process.platform === 'win32') {
      // Windows PowerShell SendKeys for Ctrl+V
      const psCommand = `powershell -command "$wshell = New-Object -ComObject wscript.shell; $wshell.SendKeys('^v')"`;
      exec(psCommand, () => {});
    } else if (process.platform === 'darwin') {
      // macOS AppleScript for Cmd+V
      const osaCommand = `osascript -e 'tell application "System Events" to keystroke "v" using command down'`;
      exec(osaCommand, () => {});
    }
  }, 250);
}

// IPC Handlers
ipcMain.handle('gemini:translate', async (_, { text, sourceLang, targetLang }) => {
  return await geminiTranslator.translate(text, sourceLang, targetLang);
});

ipcMain.handle('gemini:voice-command', async (_, { voiceInput }) => {
  return await voiceCommandEngine.process(voiceInput);
});

ipcMain.handle('gemini:smart-action', async (_, { text, actionType }) => {
  return await voiceCommandEngine.process(text, actionType);
});

ipcMain.handle('gemini:validate-key', async (_, { key }) => {
  const tempTranslator = new GeminiTranslator(key);
  return await tempTranslator.validateKey();
});

ipcMain.handle('phonetic:transliterate', async (_, { text }) => {
  return PhoneticEngine.transliterateText(text);
});

ipcMain.handle('clipboard:get-all', async (_, { query, filterPinned }) => {
  return clipboardStore.search(query, filterPinned);
});

ipcMain.handle('clipboard:add', async (_, { text, source }) => {
  return clipboardStore.addClip(text, source);
});

ipcMain.handle('clipboard:toggle-pin', async (_, { id }) => {
  return clipboardStore.togglePin(id);
});

ipcMain.handle('clipboard:delete', async (_, { id }) => {
  return clipboardStore.deleteClip(id);
});

ipcMain.handle('clipboard:clear', async (_, { keepPinned }) => {
  return clipboardStore.clearHistory(keepPinned);
});

ipcMain.handle('desktop:inject-text', async (_, { text }) => {
  injectTextToActiveWindow(text);
  return true;
});

ipcMain.handle('settings:get', async () => {
  return appSettings;
});

ipcMain.handle('settings:save', async (_, { settings }) => {
  appSettings = { ...appSettings, ...settings };
  geminiTranslator.setApiKey(appSettings.geminiApiKey);
  voiceCommandEngine.setApiKey(appSettings.geminiApiKey);
  if (mainWindow) {
    mainWindow.setAlwaysOnTop(appSettings.alwaysOnTop);
  }
  try {
    fs.writeFileSync(settingsFile, JSON.stringify(appSettings, null, 2), 'utf8');
  } catch (e) {
    console.error('Failed to save settings:', e);
  }
  registerGlobalHotkeys();
  return appSettings;
});

ipcMain.handle('window:hide', () => {
  if (mainWindow) mainWindow.hide();
});

ipcMain.handle('window:minimize', () => {
  if (mainWindow) mainWindow.minimize();
});

// App Lifecycle
app.whenReady().then(() => {
  createWindow();
  try {
    createTray();
  } catch (e) {
    console.warn('Tray icon initialization notice:', e.message);
  }
  registerGlobalHotkeys();
  startClipboardWatcher();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});
