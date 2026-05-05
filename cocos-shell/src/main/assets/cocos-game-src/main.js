// Demo UI wiring — the JS-side equivalent of the original ggr's Cocos
// Creator scene script. Tap Start → ask Native to record → render the
// scored result when Native calls back into us.
(function () {
  var startBtn  = document.getElementById("start");
  var stopBtn   = document.getElementById("stop");
  var statusEl  = document.getElementById("status");
  var refEl     = document.getElementById("ref");
  var scoreCard = document.getElementById("score-card");
  var scoreEl   = document.getElementById("score");
  var srcTag    = document.getElementById("src-tag");
  var wordsEl   = document.getElementById("words");

  function colorFor(score) {
    if (score >= 85) return "high";
    if (score >= 60) return "mid";
    return "low";
  }

  startBtn.addEventListener("click", function () {
    statusEl.textContent = "recording... speak now.";
    scoreCard.classList.remove("show");
    wordsEl.innerHTML = "";
    jsb.reflection.callStaticMethod(
      "ReadingJsb",
      "startRecording",
      refEl.textContent,
      "demo-" + Date.now(),
      "sentence"
    );
  });

  stopBtn.addEventListener("click", function () {
    statusEl.textContent = "stopping...";
    jsb.reflection.callStaticMethod("ReadingJsb", "stopRecording");
  });

  document.addEventListener("record-result", function (ev) {
    var r = ev.detail || {};
    statusEl.textContent = "done.";
    scoreEl.firstChild.nodeValue = String(r.score || 0);
    scoreEl.className = colorFor(r.score || 0);
    srcTag.textContent = "scorer";

    var words = (r.Words || []);
    wordsEl.innerHTML = words.map(function (w) {
      var s = (w.score == null) ? 0 : w.score;
      return '<span class="word ' + colorFor(s) + '">'
           + escapeHtml(w.char) + ': ' + s + '</span>';
    }).join("");
    scoreCard.classList.add("show");
  });

  document.addEventListener("record-error", function (ev) {
    statusEl.textContent = "error: " + (ev.detail || "unknown");
  });

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"})[c];
    });
  }
})();
