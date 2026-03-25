export default function SettingsPage() {
  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">Impostazioni</h1>
        <p className="text-sm text-slate-500 mt-1 dark:text-slate-400">Configurazione del sistema AI</p>
      </div>

      <div className="bg-white/80 backdrop-blur-sm rounded-2xl border border-slate-200/60 p-8 dark:bg-slate-800/80 dark:border-slate-700/60">
        <div className="max-w-lg mx-auto text-center">
          <div className="w-16 h-16 bg-gradient-to-br from-orange-100 to-orange-200 rounded-2xl flex items-center justify-center mx-auto mb-5 dark:from-orange-900 dark:to-orange-800">
            <svg className="w-8 h-8 text-orange-600 dark:text-orange-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 0 0-2.455 2.456Z" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-slate-900 mb-2 dark:text-slate-100">Modello Fisso</h2>
          <p className="text-sm text-slate-500 mb-8 dark:text-slate-400">
            Il sistema utilizza Ollama con modelli pre-configurati. Non e necessaria alcuna configurazione.
          </p>

          <div className="grid gap-3 text-left">
            <div className="flex items-center gap-4 bg-slate-50 rounded-xl p-4 border border-slate-100 dark:bg-slate-700/50 dark:border-slate-600">
              <div className="w-10 h-10 bg-gradient-to-br from-orange-400 to-orange-600 rounded-xl flex items-center justify-center shrink-0">
                <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z" />
                </svg>
              </div>
              <div>
                <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">Chat: llama3</div>
                <div className="text-xs text-slate-500 mt-0.5 dark:text-slate-400">Modello per le risposte conversazionali</div>
              </div>
              <span className="ml-auto px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-orange-50 text-orange-600 border border-orange-200 dark:bg-orange-950/50 dark:text-orange-400 dark:border-orange-800">
                Ollama
              </span>
            </div>

            <div className="flex items-center gap-4 bg-slate-50 rounded-xl p-4 border border-slate-100 dark:bg-slate-700/50 dark:border-slate-600">
              <div className="w-10 h-10 bg-gradient-to-br from-violet-400 to-violet-600 rounded-xl flex items-center justify-center shrink-0">
                <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
                </svg>
              </div>
              <div>
                <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">Embedding: nomic-embed-text</div>
                <div className="text-xs text-slate-500 mt-0.5 dark:text-slate-400">Modello per la ricerca semantica</div>
              </div>
              <span className="ml-auto px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-orange-50 text-orange-600 border border-orange-200 dark:bg-orange-950/50 dark:text-orange-400 dark:border-orange-800">
                Ollama
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
