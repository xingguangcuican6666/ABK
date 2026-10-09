#include "my_application.h"

int main(int argc, char** argv) {
  // WebKitGTK (module WebUI window + embedded code view) crashes on Wayland with
  // "protocol error 71" when the DMABUF renderer picks an unsupported buffer
  // path. Force the safer renderer BEFORE any WebKit web process is spawned —
  // setting it later (e.g. when the first WebUI window opens) is too late once a
  // web process already exists.
  g_setenv("WEBKIT_DISABLE_DMABUF_RENDERER", "1", TRUE);

  g_autoptr(MyApplication) app = my_application_new();
  return g_application_run(G_APPLICATION(app), argc, argv);
}
