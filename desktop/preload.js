// Lets the page hand its reminders to the Windows app.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('desktop', {
  schedule: (list) => ipcRenderer.send('schedule', list)
});
