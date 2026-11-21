const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("cloudSetup", {
  status: () => ipcRenderer.invoke("cloud:status"),
  save: (cfg) => ipcRenderer.invoke("cloud:save", cfg),
  syncNow: () => ipcRenderer.invoke("cloud:sync-now"),
  resetPassword: (email) => ipcRenderer.invoke("cloud:reset-password", email),
  clear: () => ipcRenderer.invoke("cloud:clear")
});
