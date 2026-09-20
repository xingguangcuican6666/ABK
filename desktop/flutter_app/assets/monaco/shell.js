(function () {
  const hostBridge =
    globalThis.webkit &&
    globalThis.webkit.messageHandlers &&
    globalThis.webkit.messageHandlers.abk;
  const postHostMessage = (message) => {
    try {
      hostBridge && hostBridge.postMessage(message);
    } catch (error) {
      console.error("ABK Monaco bridge error", error);
    }
  };

  const editorState = {
    editor: null,
    model: null,
    followTail: false,
    maxRetainedLines: null,
    theme: "light",
    language: "plaintext",
    themeData: null,
  };

  const defaultThemeData = {
    background: "#f6f8fa",
    foreground: "#111827",
    lineNumber: "#94a3b8",
    activeLineNumber: "#334155",
    gutterBackground: "#f6f8fa",
    widgetBackground: "#ffffff",
    selectionBackground: "#bfdbfe",
    inactiveSelectionBackground: "#dbeafe",
    comment: "#6b7280",
    string: "#0f766e",
    number: "#b45309",
    keyword: "#1d4ed8",
  };

  const isNearBottom = () => {
    if (!editorState.editor) {
      return false;
    }
    const editor = editorState.editor;
    const layout = editor.getLayoutInfo();
    const distance =
      editor.getScrollHeight() - (editor.getScrollTop() + layout.height);
    return distance <= 24;
  };

  const scrollToBottom = () => {
    if (!editorState.editor) {
      return;
    }
    const editor = editorState.editor;
    editor.setScrollTop(editor.getScrollHeight());
  };

  const trimModelToRetentionLimit = () => {
    const { model, maxRetainedLines } = editorState;
    if (!model || !maxRetainedLines || maxRetainedLines < 1) {
      return;
    }
    const lineCount = model.getLineCount();
    if (lineCount <= maxRetainedLines) {
      return;
    }
    const deleteThroughLine = lineCount - maxRetainedLines + 1;
    model.applyEdits([
      {
        range: new monaco.Range(1, 1, deleteThroughLine, 1),
        text: "",
      },
    ]);
  };

  const ensureModel = (content, language) => {
    const nextLanguage = language || "plaintext";
    if (!editorState.model) {
      editorState.model = monaco.editor.createModel(content || "", nextLanguage);
      editorState.editor.setModel(editorState.model);
      return;
    }
    if (editorState.language !== nextLanguage) {
      monaco.editor.setModelLanguage(editorState.model, nextLanguage);
    }
    editorState.language = nextLanguage;
  };

  const normalizeThemeData = (themeData) => ({
    ...defaultThemeData,
    ...(themeData || {}),
  });

  const applyTheme = (theme, themeData) => {
    editorState.theme = theme === "dark" ? "dark" : "light";
    editorState.themeData = normalizeThemeData(themeData);
    const palette = editorState.themeData;
    monaco.editor.defineTheme("abk-theme", {
      base: editorState.theme === "dark" ? "vs-dark" : "vs",
      inherit: true,
      rules: [
        { token: "comment", foreground: palette.comment.slice(1) },
        { token: "string", foreground: palette.string.slice(1) },
        { token: "number", foreground: palette.number.slice(1) },
        { token: "keyword", foreground: palette.keyword.slice(1) },
      ],
      colors: {
        "editor.background": palette.background,
        "editor.foreground": palette.foreground,
        "editorLineNumber.foreground": palette.lineNumber,
        "editorLineNumber.activeForeground": palette.activeLineNumber,
        "editorGutter.background": palette.gutterBackground,
        "editorWidget.background": palette.widgetBackground,
        "editor.selectionBackground": palette.selectionBackground,
        "editor.inactiveSelectionBackground": palette.inactiveSelectionBackground,
      },
    });
    document.body.style.background = palette.background;
    monaco.editor.setTheme("abk-theme");
  };

  const replaceContent = (content) => {
    ensureModel(content || "", editorState.language);
    const shouldFollow = editorState.followTail || isNearBottom();
    editorState.model.setValue(content || "");
    trimModelToRetentionLimit();
    if (shouldFollow) {
      requestAnimationFrame(scrollToBottom);
    }
  };

  const appendContent = (content) => {
    if (!content) {
      return;
    }
    ensureModel("", editorState.language);
    const shouldFollow = editorState.followTail || isNearBottom();
    const lineCount = editorState.model.getLineCount();
    const endColumn = editorState.model.getLineMaxColumn(lineCount);
    editorState.model.applyEdits([
      {
        range: new monaco.Range(lineCount, endColumn, lineCount, endColumn),
        text: content,
      },
    ]);
    trimModelToRetentionLimit();
    if (shouldFollow) {
      requestAnimationFrame(scrollToBottom);
    }
  };

  const handleHostMessage = (raw) => {
    const message = typeof raw === "string" ? JSON.parse(raw) : raw;
    switch (message.type) {
      case "initialize":
        editorState.followTail = message.followTail === true;
        editorState.maxRetainedLines =
          typeof message.maxRetainedLines === "number"
            ? message.maxRetainedLines
            : null;
        editorState.language = message.language || "plaintext";
        ensureModel(message.content || "", editorState.language);
        applyTheme(message.theme, message.themeData);
        replaceContent(message.content || "");
        break;
      case "replaceContent":
        replaceContent(message.content || "");
        break;
      case "appendContent":
        appendContent(message.content || "");
        break;
      case "setLanguage":
        editorState.language = message.language || "plaintext";
        ensureModel(editorState.model ? editorState.model.getValue() : "", editorState.language);
        break;
      case "setTheme":
        applyTheme(message.theme, message.themeData);
        break;
      case "setFollowTail":
        editorState.followTail = message.followTail === true;
        if (editorState.followTail) {
          requestAnimationFrame(scrollToBottom);
        }
        break;
      case "setRetentionLimit":
        editorState.maxRetainedLines =
          typeof message.maxRetainedLines === "number"
            ? message.maxRetainedLines
            : null;
        trimModelToRetentionLimit();
        break;
      default:
        console.warn("Unknown Monaco host message", message);
    }
  };

  const boot = () => {
    const container = document.getElementById("container");
    editorState.editor = monaco.editor.create(container, {
      value: "",
      language: "plaintext",
      automaticLayout: true,
      readOnly: true,
      lineNumbers: "on",
      lineNumbersMinChars: 4,
      glyphMargin: false,
      folding: true,
      scrollBeyondLastLine: false,
      minimap: { enabled: false },
      renderLineHighlight: "none",
      wordWrap: "off",
      stickyScroll: { enabled: false },
      find: {
        addExtraSpaceOnTop: false,
      },
    });
    editorState.editor.onDidScrollChange(() => {
      editorState.followTail = isNearBottom();
    });
    globalThis.abkMonaco = {
      handleHostMessage,
    };
    postHostMessage("ready");
  };

  require.config({ paths: { vs: "./vs" } });
  require(["vs/editor/editor.main"], boot, (error) => {
    console.error("Failed to boot Monaco", error);
  });
})();
