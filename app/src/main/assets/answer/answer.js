(function () {
  var root = document.getElementById("root");
  var bridge = window.Android;
  var STICK_PX = 48;
  var stick = true;
  var wrap = false;
  var dark = null;
  var latest = null;
  var timer = 0;
  var shown = {};
  var mermaidSeq = 0;

  function esc(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value; } catch (e) {}
    }
    return esc(code);
  }

  var md = markdownit({ html: false, linkify: true, typographer: true });
  md.use(texmath, { engine: katex, delimiters: ["dollars", "brackets"] });

  md.renderer.rules.fence = function (tokens, idx, options, env) {
    var t = tokens[idx];
    var lang = t.info.trim().split(/\s+/)[0].toLowerCase();
    var block = '<pre><code class="hljs">' + highlight(t.content, lang) + "</code></pre>";
    return lang === "mermaid" && env.final ? '<div class="mmd" data-src="' + esc(t.content) + '">' + block + "</div>" : block;
  };

  md.renderer.rules.table_open = function (tokens, idx, o, e, self) { return '<div class="table-wrap">' + self.renderToken(tokens, idx, o); };
  md.renderer.rules.table_close = function (tokens, idx, o, e, self) { return self.renderToken(tokens, idx, o) + "</div>"; };

  var CITE = /\[([^\[\]]+?)\s+p\.\s*(\d+)[^\]]*\]/y;
  md.inline.ruler.before("link", "citation", function (state, silent) {
    if (state.src.charCodeAt(state.pos) !== 0x5b) return false;
    CITE.lastIndex = state.pos;
    var m = CITE.exec(state.src);
    if (!m || state.src.charCodeAt(CITE.lastIndex) === 0x28) return false;
    if (!silent) state.push("citation", "", 0).meta = { title: m[1].trim(), page: parseInt(m[2], 10) };
    state.pos = CITE.lastIndex;
    return true;
  });
  // Citations number by document in order of first mention; the inline pill keeps the exact page for the preview.
  function sourceNumber(env, title, page) {
    env.sources = env.sources || [];
    var src = env.sources.find(function (x) { return x.title.toLowerCase() === title.toLowerCase(); });
    if (!src) {
      src = { n: env.sources.length + 1, title: title, pages: [] };
      env.sources.push(src);
    }
    if (src.pages.indexOf(page) < 0) src.pages.push(page);
    return src.n;
  }

  md.renderer.rules.citation = function (tokens, idx, options, env) {
    var c = tokens[idx].meta;
    var n = sourceNumber(env, c.title, c.page);
    // Back-to-back citations of the same document show one number; the Sources list keeps every page.
    var j = idx - 1;
    while (j >= 0 && tokens[j].type === "text" && !tokens[j].content.trim()) j--;
    if (j >= 0 && tokens[j].type === "citation" && tokens[j].meta.title.toLowerCase() === c.title.toLowerCase()) return "";
    return '<button class="cite" data-title="' + esc(c.title) + '" data-page="' + c.page + '" aria-label="' + esc(c.title) + ", page " + c.page + '">' + n + "</button>";
  };

  function sourcesHtml(sources) {
    if (!sources || !sources.length) return "";
    return '<div class="sources"><div class="sources-label">Sources</div>' + sources.map(function (s) {
      var pages = s.pages.slice().sort(function (x, y) { return x - y; }).map(function (p) {
        return '<button class="cite page" data-title="' + esc(s.title) + '" data-page="' + p + '">p.' + p + "</button>";
      }).join("");
      return '<div class="source"><span class="source-n">' + s.n + '</span><div class="source-body"><span class="source-title">' + esc(s.title) + "</span>" + pages + "</div></div>";
    }).join("") + "</div>";
  }

  function messageNode(m, live) {
    var key = m.role + "|" + (live ? 1 : 0) + "|" + m.markdown;
    var rec = shown[m.id];
    if (rec && rec.key === key) return rec.node;
    var node = rec ? rec.node : document.createElement("div");
    node.className = "msg " + m.role + (live ? " live" : "");
    if (m.role === "assistant") {
      var env = { final: !live };
      node.innerHTML = md.render(m.markdown, env) + sourcesHtml(env.sources);
      renderDiagrams(node);
    } else {
      node.textContent = m.markdown;
    }
    shown[m.id] = { key: key, node: node };
    return node;
  }

  function renderDiagrams(node) {
    node.querySelectorAll(".mmd").forEach(function (box) {
      var id = "mmd" + mermaidSeq++;
      mermaid.render(id, box.getAttribute("data-src")).then(function (r) {
        box.innerHTML = r.svg;
        report();
      }).catch(function () {
        var junk = document.getElementById("d" + id);
        if (junk) junk.remove();
      });
    });
  }

  function flush() {
    timer = 0;
    var p = latest;
    var ids = {};
    var prev = null;
    p.messages.forEach(function (m, i) {
      ids[m.id] = true;
      var node = messageNode(m, p.streaming && i === p.messages.length - 1);
      var want = prev ? prev.nextSibling : root.firstChild;
      if (node !== want) root.insertBefore(node, want);
      prev = node;
    });
    Object.keys(shown).forEach(function (id) {
      if (!ids[id]) { shown[id].node.remove(); delete shown[id]; }
    });
    if (!wrap && stick) window.scrollTo(0, document.documentElement.scrollHeight);
    report();
  }

  function schedule() {
    if (latest && !timer) timer = setTimeout(flush, 16);
  }

  function report() {
    if (wrap && bridge) bridge.post("height", String(Math.ceil(root.getBoundingClientRect().height)), "");
  }

  window.render = function (payload) {
    latest = payload;
    schedule();
  };

  window.setTheme = function (t) {
    var style = document.documentElement.style;
    Object.keys(t.vars).forEach(function (k) { style.setProperty("--" + k, t.vars[k]); });
    wrap = !!t.wrap;
    document.documentElement.classList.toggle("wrap", wrap);
    mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: t.dark ? "dark" : "default", fontFamily: t.vars.body });
    if (dark !== null && dark !== t.dark) {
      Object.keys(shown).forEach(function (id) { shown[id].key = ""; });
      schedule();
    }
    dark = t.dark;
    report();
  };

  window.addEventListener("scroll", function () {
    stick = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - STICK_PX;
  }, { passive: true });

  if (window.ResizeObserver) new ResizeObserver(report).observe(root);

  document.addEventListener("click", function (e) {
    var a = e.target.closest("a[href], button.cite, [data-action]");
    if (!a || !bridge) return;
    e.preventDefault();
    if (a.matches("button.cite")) bridge.post("citation", a.dataset.title, a.dataset.page);
    else if (a.dataset.action) bridge.post("action", a.dataset.action, "");
    else bridge.post("link", a.href, "");
  });
})();
