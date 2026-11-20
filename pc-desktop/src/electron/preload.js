const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("aromaElectron", {
  setPosActivity: (active) => ipcRenderer.send("pos-activity-state", Boolean(active)),
  installPendingUpdate: () => ipcRenderer.invoke("pc-install-update"),
  exportInvoicePdf: (invoiceNumber) => ipcRenderer.invoke("export-invoice-pdf", invoiceNumber),
  onUpdateReady: (cb) => ipcRenderer.on("pc-update-ready", (_, data) => cb(data))
});
