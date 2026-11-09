// Schedule Maker for Windows: a window around the app, a tray icon, and reminders that pop up even when the window is closed.
const { app, BrowserWindow, Tray, Menu, Notification, shell, ipcMain, nativeImage } = require('electron');
const path = require('path');

let win = null;
let tray = null;
let quitting = false;
let timers = [];
const startHidden = process.argv.includes('--hidden');

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => show());
}

app.setAppUserModelId('com.irotheangel.schedulemaker');

function iconPath() {
  return path.join(__dirname, 'app', 'icons', 'icon-512.png');
}

function createWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 820,
    minWidth: 380,
    minHeight: 560,
    title: 'Schedule Maker',
    backgroundColor: '#161615',
    icon: iconPath(),
    autoHideMenuBar: true,
    show: !startHidden,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true
    }
  });
  win.removeMenu();
  win.loadFile(path.join(__dirname, 'app', 'index.html'));

  // Links (like Ask Claude) open in your normal browser.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (/^https?:/.test(url)) {
      e.preventDefault();
      shell.openExternal(url);
    }
  });

  // Closing the window keeps the app in the tray so reminders still arrive.
  let toldAboutTray = false;
  win.on('close', (e) => {
    if (quitting) return;
    e.preventDefault();
    win.hide();
    if (!toldAboutTray && Notification.isSupported()) {
      toldAboutTray = true;
      new Notification({ title: 'Schedule Maker is still running', body: 'It stays in the tray so your reminders keep coming. Right-click the tray icon to quit.', icon: iconPath() }).show();
    }
  });
}

function show() {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
}

function buildTray() {
  tray = new Tray(nativeImage.createFromPath(iconPath()).resize({ width: 16, height: 16 }));
  tray.setToolTip('Schedule Maker');
  const startsWithWindows = app.getLoginItemSettings().openAtLogin;
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Open Schedule Maker', click: show },
    {
      label: 'Start with Windows',
      type: 'checkbox',
      checked: startsWithWindows,
      click: (item) => app.setLoginItemSettings({ openAtLogin: item.checked, args: ['--hidden'] })
    },
    { label: 'Get the newest version', click: () => shell.openExternal('https://github.com/IroTheAngel/schedule-maker/releases/latest') },
    { type: 'separator' },
    { label: 'Quit', click: () => { quitting = true; app.quit(); } }
  ]));
  tray.on('click', show);
}

// The page sends the list of upcoming reminders whenever the plan changes.
ipcMain.on('schedule', (_e, list) => {
  timers.forEach(clearTimeout);
  timers = [];
  const now = Date.now();
  (Array.isArray(list) ? list : []).slice(0, 80).forEach((n) => {
    const wait = Number(n.at) - now;
    if (!(wait > 0) || wait > 3 * 24 * 3600 * 1000) return;
    timers.push(setTimeout(() => {
      if (!Notification.isSupported()) return;
      const note = new Notification({ title: String(n.title || ''), body: String(n.body || ''), icon: iconPath(), urgency: 'critical' });
      note.on('click', show);
      note.show();
    }, wait));
  });
});

app.whenReady().then(() => {
  // First run: start with Windows (hidden in the tray) so reminders work after a restart.
  const settings = app.getLoginItemSettings();
  if (app.isPackaged && !settings.openAtLogin && !require('fs').existsSync(path.join(app.getPath('userData'), '.configured'))) {
    app.setLoginItemSettings({ openAtLogin: true, args: ['--hidden'] });
    try { require('fs').writeFileSync(path.join(app.getPath('userData'), '.configured'), '1'); } catch (e) {}
  }
  createWindow();
  buildTray();
});

app.on('before-quit', () => { quitting = true; });
app.on('window-all-closed', (e) => e.preventDefault());
