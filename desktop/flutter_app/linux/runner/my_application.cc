#include "my_application.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <flutter_linux/flutter_linux.h>
#include <gio/gio.h>
#include <jsc/jsc.h>
#include <webkit2/webkit2.h>
#ifdef GDK_WINDOWING_X11
#include <gdk/gdkx.h>
#endif

#include "flutter/generated_plugin_registrant.h"

struct _MyApplication {
  GtkApplication parent_instance;
  char **dart_entrypoint_arguments;
  FlView *view;
  GtkOverlay *overlay;
  FlMethodChannel *platform_channel;
  GHashTable *embedded_code_views;
};

typedef struct {
  MyApplication *application;
  gchar *url;
  gchar *title;
} PendingWebUiWindow;

typedef struct {
  gchar *view_id;
  WebKitUserContentManager *manager;
  WebKitWebView *web_view;
  GPtrArray *pending_messages;
  gboolean ready;
} EmbeddedCodeView;

G_DEFINE_TYPE(MyApplication, my_application, GTK_TYPE_APPLICATION)

static gchar *read_gsettings_string(const gchar *schema, const gchar *key) {
  GSettingsSchemaSource *source = g_settings_schema_source_get_default();
  if (source == nullptr) {
    return nullptr;
  }
  GSettingsSchema *settings_schema =
      g_settings_schema_source_lookup(source, schema, TRUE);
  if (settings_schema == nullptr) {
    return nullptr;
  }
  g_settings_schema_unref(settings_schema);
  g_autoptr(GSettings) settings = g_settings_new(schema);
  gchar *value = g_settings_get_string(settings, key);
  if (value == nullptr || value[0] == '\0') {
    g_free(value);
    return nullptr;
  }
  return value;
}

static gchar *strip_uri_prefix(gchar *value) {
  if (value == nullptr) {
    return nullptr;
  }
  if (g_str_has_prefix(value, "file://")) {
    g_autofree gchar *unescaped = g_uri_unescape_string(value + 7, nullptr);
    g_free(value);
    if (unescaped == nullptr || unescaped[0] == '\0') {
      return nullptr;
    }
    return g_strdup(unescaped);
  }
  return value;
}

static gchar *get_wallpaper_path() {
  const gchar *env_wallpaper = g_getenv("ABK_DESKTOP_WALLPAPER");
  if (env_wallpaper != nullptr && env_wallpaper[0] != '\0') {
    return g_strdup(env_wallpaper);
  }

  const struct {
    const gchar *schema;
    const gchar *key;
  } candidates[] = {
      {"org.gnome.desktop.background", "picture-uri-dark"},
      {"org.gnome.desktop.background", "picture-uri"},
      {"org.cinnamon.desktop.background", "picture-uri"},
      {"org.mate.background", "picture-filename"},
      {"org.xfce.desktop", "last-image"},
  };

  for (guint index = 0; index < G_N_ELEMENTS(candidates); index++) {
    g_autofree gchar *value =
        read_gsettings_string(candidates[index].schema, candidates[index].key);
    if (value == nullptr) {
      continue;
    }
    value = strip_uri_prefix(value);
    if (value != nullptr && g_file_test(value, G_FILE_TEST_EXISTS)) {
      return g_strdup(value);
    }
  }

  g_autofree gchar *plasma_config =
      g_build_filename(g_get_home_dir(), ".config",
                       "plasma-org.kde.plasma.desktop-appletsrc", nullptr);
  if (g_file_test(plasma_config, G_FILE_TEST_EXISTS)) {
    g_autofree gchar *contents = nullptr;
    gsize length = 0;
    if (g_file_get_contents(plasma_config, &contents, &length, nullptr)) {
      g_auto(GStrv) lines = g_strsplit(contents, "\n", -1);
      for (guint index = 0; lines[index] != nullptr; index++) {
        const gchar *prefix = "Image=file://";
        if (g_str_has_prefix(lines[index], prefix)) {
          g_autofree gchar *path =
              g_uri_unescape_string(lines[index] + strlen(prefix), nullptr);
          if (path != nullptr && g_file_test(path, G_FILE_TEST_EXISTS)) {
            return g_strdup(path);
          }
        }
      }
    }
  }

  return nullptr;
}

static const gchar *fl_value_lookup_string_value(FlValue *map,
                                                 const gchar *key) {
  if (map == nullptr || fl_value_get_type(map) != FL_VALUE_TYPE_MAP) {
    return nullptr;
  }
  FlValue *value = fl_value_lookup_string(map, key);
  if (value == nullptr || fl_value_get_type(value) != FL_VALUE_TYPE_STRING) {
    return nullptr;
  }
  return fl_value_get_string(value);
}

static gboolean fl_value_lookup_double_value(FlValue *map, const gchar *key,
                                             gdouble *out) {
  if (map == nullptr || fl_value_get_type(map) != FL_VALUE_TYPE_MAP ||
      out == nullptr) {
    return FALSE;
  }
  FlValue *value = fl_value_lookup_string(map, key);
  if (value == nullptr) {
    return FALSE;
  }
  if (fl_value_get_type(value) == FL_VALUE_TYPE_FLOAT) {
    *out = fl_value_get_float(value);
    return TRUE;
  }
  if (fl_value_get_type(value) == FL_VALUE_TYPE_INT) {
    *out = static_cast<gdouble>(fl_value_get_int(value));
    return TRUE;
  }
  return FALSE;
}

static gboolean fl_value_lookup_bool_value(FlValue *map, const gchar *key,
                                           gboolean *out) {
  if (map == nullptr || fl_value_get_type(map) != FL_VALUE_TYPE_MAP ||
      out == nullptr) {
    return FALSE;
  }
  FlValue *value = fl_value_lookup_string(map, key);
  if (value == nullptr || fl_value_get_type(value) != FL_VALUE_TYPE_BOOL) {
    return FALSE;
  }
  *out = fl_value_get_bool(value);
  return TRUE;
}

static gchar *get_bundle_data_path(const gchar *relative_path) {
  if (relative_path == nullptr || relative_path[0] == '\0') {
    return nullptr;
  }
  g_autofree gchar *executable_path =
      g_file_read_link("/proc/self/exe", nullptr);
  if (executable_path == nullptr) {
    return nullptr;
  }
  g_autofree gchar *executable_dir = g_path_get_dirname(executable_path);
  g_autofree gchar *asset_path =
      g_build_filename(executable_dir, "data", relative_path, nullptr);
  if (!g_file_test(asset_path, G_FILE_TEST_EXISTS)) {
    return nullptr;
  }
  return g_strdup(asset_path);
}

static gchar *get_monaco_shell_uri() {
  g_autofree gchar *shell_path = get_bundle_data_path("monaco/index.html");
  if (shell_path == nullptr) {
    return nullptr;
  }
  return g_filename_to_uri(shell_path, nullptr, nullptr);
}

static void embedded_code_view_javascript_finished_cb(GObject *object,
                                                      GAsyncResult *result,
                                                      gpointer user_data) {
  (void)user_data;
  g_autoptr(GError) error = nullptr;
  g_autoptr(JSCValue) value = webkit_web_view_evaluate_javascript_finish(
      WEBKIT_WEB_VIEW(object), result, &error);
  if (error != nullptr) {
    g_warning("Embedded code view JS execution failed: %s", error->message);
  }
  (void)value;
}

static void embedded_code_view_dispatch_message(EmbeddedCodeView *view,
                                                const gchar *message) {
  if (view == nullptr || message == nullptr || view->web_view == nullptr) {
    return;
  }
  g_autofree gchar *escaped = g_strescape(message, nullptr);
  g_autofree gchar *script = g_strdup_printf(
      "window.abkMonaco && window.abkMonaco.handleHostMessage(\"%s\");",
      escaped != nullptr ? escaped : "");
  webkit_web_view_evaluate_javascript(
      view->web_view, script, -1, nullptr, nullptr, nullptr,
      embedded_code_view_javascript_finished_cb, nullptr);
}

static void embedded_code_view_flush_pending_messages(EmbeddedCodeView *view) {
  if (view == nullptr || !view->ready || view->pending_messages == nullptr) {
    return;
  }
  for (guint index = 0; index < view->pending_messages->len; index++) {
    embedded_code_view_dispatch_message(
        view, static_cast<const gchar *>(
                  g_ptr_array_index(view->pending_messages, index)));
  }
  g_ptr_array_set_size(view->pending_messages, 0);
}

static void
embedded_code_view_script_message_received_cb(WebKitUserContentManager *manager,
                                              WebKitJavascriptResult *js_result,
                                              gpointer user_data) {
  (void)manager;
  EmbeddedCodeView *view = static_cast<EmbeddedCodeView *>(user_data);
  if (view == nullptr || js_result == nullptr) {
    return;
  }
  JSCValue *js_value = webkit_javascript_result_get_js_value(js_result);
  if (js_value == nullptr || !jsc_value_is_string(js_value)) {
    return;
  }
  g_autofree gchar *message = jsc_value_to_string(js_value);
  if (g_strcmp0(message, "ready") != 0) {
    return;
  }
  view->ready = TRUE;
  embedded_code_view_flush_pending_messages(view);
}

static EmbeddedCodeView *embedded_code_view_new(MyApplication *self,
                                                const gchar *view_id) {
  if (self == nullptr || self->overlay == nullptr || view_id == nullptr ||
      view_id[0] == '\0') {
    return nullptr;
  }
  g_autofree gchar *shell_uri = get_monaco_shell_uri();
  if (shell_uri == nullptr) {
    g_warning("Monaco shell asset is missing; embedded code view disabled.");
    return nullptr;
  }

  EmbeddedCodeView *view = g_new0(EmbeddedCodeView, 1);
  view->view_id = g_strdup(view_id);
  view->pending_messages = g_ptr_array_new_with_free_func(g_free);
  view->manager = webkit_user_content_manager_new();
  if (!webkit_user_content_manager_register_script_message_handler(
          view->manager, "abk")) {
    g_warning("Failed to register Monaco script message handler.");
    g_ptr_array_unref(view->pending_messages);
    g_free(view->view_id);
    g_free(view);
    return nullptr;
  }
  g_signal_connect(view->manager, "script-message-received::abk",
                   G_CALLBACK(embedded_code_view_script_message_received_cb),
                   view);

  view->web_view = WEBKIT_WEB_VIEW(
      webkit_web_view_new_with_user_content_manager(view->manager));
  WebKitSettings *settings = webkit_web_view_get_settings(view->web_view);
  webkit_settings_set_enable_developer_extras(settings, TRUE);
  webkit_settings_set_javascript_can_access_clipboard(settings, TRUE);
  webkit_settings_set_enable_write_console_messages_to_stdout(settings, TRUE);

  GtkWidget *widget = GTK_WIDGET(view->web_view);
  gtk_widget_set_halign(widget, GTK_ALIGN_START);
  gtk_widget_set_valign(widget, GTK_ALIGN_START);
  gtk_widget_set_size_request(widget, 1, 1);
  gtk_widget_set_margin_start(widget, 0);
  gtk_widget_set_margin_top(widget, 0);
  gtk_widget_show(widget);
  gtk_overlay_add_overlay(self->overlay, widget);

  webkit_web_view_load_uri(view->web_view, shell_uri);
  return view;
}

static void embedded_code_view_post_message(EmbeddedCodeView *view,
                                            const gchar *message) {
  if (view == nullptr || message == nullptr || message[0] == '\0') {
    return;
  }
  if (!view->ready) {
    g_ptr_array_add(view->pending_messages, g_strdup(message));
    return;
  }
  embedded_code_view_dispatch_message(view, message);
}

static void embedded_code_view_update_frame(EmbeddedCodeView *view,
                                            gdouble left, gdouble top,
                                            gdouble width, gdouble height,
                                            gboolean visible) {
  if (view == nullptr || view->web_view == nullptr) {
    return;
  }
  GtkWidget *widget = GTK_WIDGET(view->web_view);
  const gint x = std::max(0, static_cast<gint>(std::lround(left)));
  const gint y = std::max(0, static_cast<gint>(std::lround(top)));
  const gint widget_width =
      std::max(1, static_cast<gint>(std::ceil(std::max(width, 1.0))));
  const gint widget_height =
      std::max(1, static_cast<gint>(std::ceil(std::max(height, 1.0))));
  gtk_widget_set_margin_start(widget, x);
  gtk_widget_set_margin_top(widget, y);
  gtk_widget_set_size_request(widget, widget_width, widget_height);
  if (visible) {
    gtk_widget_show(widget);
  } else {
    gtk_widget_hide(widget);
  }
}

static void embedded_code_view_free(gpointer data) {
  EmbeddedCodeView *view = static_cast<EmbeddedCodeView *>(data);
  if (view == nullptr) {
    return;
  }
  if (view->web_view != nullptr) {
    GtkWidget *widget = GTK_WIDGET(view->web_view);
    GtkWidget *parent = gtk_widget_get_parent(widget);
    if (GTK_IS_CONTAINER(parent)) {
      gtk_container_remove(GTK_CONTAINER(parent), widget);
    }
  }
  if (view->manager != nullptr) {
    webkit_user_content_manager_unregister_script_message_handler(view->manager,
                                                                  "abk");
    g_object_unref(view->manager);
  }
  if (view->pending_messages != nullptr) {
    g_ptr_array_unref(view->pending_messages);
  }
  g_free(view->view_id);
  g_free(view);
}

static FlMethodResponse *
handle_platform_method_call(MyApplication *self, FlMethodCall *method_call) {
  const gchar *method = fl_method_call_get_name(method_call);
  if (strcmp(method, "getWallpaperPath") == 0) {
    g_autofree gchar *wallpaper_path = get_wallpaper_path();
    g_autoptr(FlValue) result = wallpaper_path == nullptr
                                    ? fl_value_new_null()
                                    : fl_value_new_string(wallpaper_path);
    return FL_METHOD_RESPONSE(fl_method_success_response_new(result));
  }

  if (strcmp(method, "openWebUiWindow") == 0) {
    FlValue *args = fl_method_call_get_args(method_call);
    if (args == nullptr || fl_value_get_type(args) != FL_VALUE_TYPE_MAP) {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "bad_args", "openWebUiWindow requires a map", nullptr));
    }

    FlValue *url_value = fl_value_lookup_string(args, "url");
    const gchar *url = url_value != nullptr && fl_value_get_type(url_value) ==
                                                   FL_VALUE_TYPE_STRING
                           ? fl_value_get_string(url_value)
                           : nullptr;
    FlValue *title_value = fl_value_lookup_string(args, "title");
    const gchar *title =
        title_value != nullptr &&
                fl_value_get_type(title_value) == FL_VALUE_TYPE_STRING
            ? fl_value_get_string(title_value)
            : "ABK WebUI";
    if (url == nullptr || url[0] == '\0') {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "bad_args", "openWebUiWindow requires a non-empty url", nullptr));
    }
    if (self == nullptr) {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "unavailable", "application instance unavailable", nullptr));
    }

    my_application_open_webui_window(self, url, title);
    return FL_METHOD_RESPONSE(
        fl_method_success_response_new(fl_value_new_bool(true)));
  }

  if (strcmp(method, "createEmbeddedCodeView") == 0) {
    FlValue *args = fl_method_call_get_args(method_call);
    const gchar *view_id = fl_value_lookup_string_value(args, "viewId");
    if (view_id == nullptr || view_id[0] == '\0') {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "bad_args", "createEmbeddedCodeView requires a non-empty viewId",
          nullptr));
    }
    if (self == nullptr || self->embedded_code_views == nullptr) {
      return FL_METHOD_RESPONSE(
          fl_method_success_response_new(fl_value_new_bool(false)));
    }
    if (g_hash_table_lookup(self->embedded_code_views, view_id) != nullptr) {
      return FL_METHOD_RESPONSE(
          fl_method_success_response_new(fl_value_new_bool(true)));
    }
    EmbeddedCodeView *view = embedded_code_view_new(self, view_id);
    if (view == nullptr) {
      return FL_METHOD_RESPONSE(
          fl_method_success_response_new(fl_value_new_bool(false)));
    }
    g_hash_table_insert(self->embedded_code_views, g_strdup(view_id), view);
    return FL_METHOD_RESPONSE(
        fl_method_success_response_new(fl_value_new_bool(true)));
  }

  if (strcmp(method, "updateEmbeddedCodeViewFrame") == 0) {
    FlValue *args = fl_method_call_get_args(method_call);
    const gchar *view_id = fl_value_lookup_string_value(args, "viewId");
    gdouble left = 0;
    gdouble top = 0;
    gdouble width = 0;
    gdouble height = 0;
    gboolean visible = FALSE;
    if (view_id == nullptr || view_id[0] == '\0' ||
        !fl_value_lookup_double_value(args, "left", &left) ||
        !fl_value_lookup_double_value(args, "top", &top) ||
        !fl_value_lookup_double_value(args, "width", &width) ||
        !fl_value_lookup_double_value(args, "height", &height) ||
        !fl_value_lookup_bool_value(args, "visible", &visible)) {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "bad_args", "updateEmbeddedCodeViewFrame received invalid args",
          nullptr));
    }
    EmbeddedCodeView *view =
        self == nullptr || self->embedded_code_views == nullptr
            ? nullptr
            : static_cast<EmbeddedCodeView *>(
                  g_hash_table_lookup(self->embedded_code_views, view_id));
    if (view != nullptr) {
      embedded_code_view_update_frame(view, left, top, width, height, visible);
    }
    return FL_METHOD_RESPONSE(
        fl_method_success_response_new(fl_value_new_bool(view != nullptr)));
  }

  if (strcmp(method, "postEmbeddedCodeViewMessage") == 0) {
    FlValue *args = fl_method_call_get_args(method_call);
    const gchar *view_id = fl_value_lookup_string_value(args, "viewId");
    const gchar *message = fl_value_lookup_string_value(args, "message");
    if (view_id == nullptr || view_id[0] == '\0' || message == nullptr) {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "bad_args", "postEmbeddedCodeViewMessage requires viewId and message",
          nullptr));
    }
    EmbeddedCodeView *view =
        self == nullptr || self->embedded_code_views == nullptr
            ? nullptr
            : static_cast<EmbeddedCodeView *>(
                  g_hash_table_lookup(self->embedded_code_views, view_id));
    if (view != nullptr) {
      embedded_code_view_post_message(view, message);
    }
    return FL_METHOD_RESPONSE(
        fl_method_success_response_new(fl_value_new_bool(view != nullptr)));
  }

  if (strcmp(method, "disposeEmbeddedCodeView") == 0) {
    FlValue *args = fl_method_call_get_args(method_call);
    const gchar *view_id = fl_value_lookup_string_value(args, "viewId");
    if (view_id == nullptr || view_id[0] == '\0') {
      return FL_METHOD_RESPONSE(fl_method_error_response_new(
          "bad_args", "disposeEmbeddedCodeView requires a non-empty viewId",
          nullptr));
    }
    const gboolean removed =
        self != nullptr && self->embedded_code_views != nullptr
            ? g_hash_table_remove(self->embedded_code_views, view_id)
            : FALSE;
    return FL_METHOD_RESPONSE(
        fl_method_success_response_new(fl_value_new_bool(removed)));
  }

  return FL_METHOD_RESPONSE(fl_method_not_implemented_response_new());
}

static void platform_method_call_cb(FlMethodChannel *channel,
                                    FlMethodCall *method_call,
                                    gpointer user_data) {
  MyApplication *self = MY_APPLICATION(user_data);
  g_autoptr(FlMethodResponse) response =
      handle_platform_method_call(self, method_call);
  g_autoptr(GError) error = nullptr;
  if (!fl_method_call_respond(method_call, response, &error)) {
    g_warning("Failed to send platform response: %s", error->message);
  }
}

static void create_channels(MyApplication *self) {
  FlEngine *engine = fl_view_get_engine(self->view);
  FlBinaryMessenger *messenger = fl_engine_get_binary_messenger(engine);
  g_autoptr(FlStandardMethodCodec) codec = fl_standard_method_codec_new();

  self->platform_channel = fl_method_channel_new(
      messenger, "com.abk.desktop/platform", FL_METHOD_CODEC(codec));
  fl_method_channel_set_method_call_handler(
      self->platform_channel, platform_method_call_cb, self, nullptr);
}

static void webui_window_destroy_cb(GtkWidget *widget, gpointer user_data) {
  (void)widget;
  WebKitUserContentManager *manager = WEBKIT_USER_CONTENT_MANAGER(user_data);
  g_object_unref(manager);
}

static gboolean open_webui_window_on_main(gpointer user_data) {
  PendingWebUiWindow *pending = static_cast<PendingWebUiWindow *>(user_data);
  if (pending == nullptr) {
    return G_SOURCE_REMOVE;
  }

  // Wayland + WebKitGTK can crash with protocol error 71 when the DMABUF
  // renderer picks an unsupported buffer path. Force the safer fallback
  // before the first WebKit view is created in this process.
  g_setenv("WEBKIT_DISABLE_DMABUF_RENDERER", "1", FALSE);

  MyApplication *self = pending->application;
  GtkWindow *window =
      GTK_WINDOW(gtk_application_window_new(GTK_APPLICATION(self)));
  gtk_window_set_default_size(window, 1280, 820);
  gtk_window_set_title(window,
                       pending->title != nullptr && pending->title[0] != '\0'
                           ? pending->title
                           : "ABK WebUI");

  GtkHeaderBar *header_bar = GTK_HEADER_BAR(gtk_header_bar_new());
  gtk_widget_show(GTK_WIDGET(header_bar));
  gtk_header_bar_set_title(header_bar, pending->title != nullptr &&
                                               pending->title[0] != '\0'
                                           ? pending->title
                                           : "ABK WebUI");
  gtk_header_bar_set_show_close_button(header_bar, TRUE);
  gtk_window_set_titlebar(window, GTK_WIDGET(header_bar));

  WebKitUserContentManager *manager = webkit_user_content_manager_new();
  g_object_ref(manager);
  WebKitWebView *web_view =
      WEBKIT_WEB_VIEW(webkit_web_view_new_with_user_content_manager(manager));
  WebKitSettings *settings = webkit_web_view_get_settings(web_view);
  webkit_settings_set_enable_developer_extras(settings, TRUE);
  webkit_settings_set_javascript_can_access_clipboard(settings, TRUE);
  webkit_settings_set_enable_write_console_messages_to_stdout(settings, TRUE);

  gtk_widget_show(GTK_WIDGET(web_view));
  gtk_container_add(GTK_CONTAINER(window), GTK_WIDGET(web_view));
  g_signal_connect(window, "destroy", G_CALLBACK(webui_window_destroy_cb),
                   manager);

  webkit_web_view_load_uri(web_view, pending->url);
  gtk_widget_show(GTK_WIDGET(window));

  g_object_unref(self);
  g_free(pending->url);
  g_free(pending->title);
  g_free(pending);
  return G_SOURCE_REMOVE;
}

void my_application_open_webui_window(MyApplication *self, const gchar *url,
                                      const gchar *title) {
  if (self == nullptr || url == nullptr || url[0] == '\0') {
    return;
  }

  PendingWebUiWindow *pending = g_new0(PendingWebUiWindow, 1);
  pending->application = MY_APPLICATION(g_object_ref(self));
  pending->url = g_strdup(url);
  pending->title =
      g_strdup(title != nullptr && title[0] != '\0' ? title : "ABK WebUI");

  g_main_context_invoke(nullptr, open_webui_window_on_main, pending);
}

// Called when first Flutter frame received.
static void first_frame_cb(MyApplication *self, FlView *view) {
  gtk_widget_show(gtk_widget_get_toplevel(GTK_WIDGET(view)));
}

// Implements GApplication::activate.
static void my_application_activate(GApplication *application) {
  MyApplication *self = MY_APPLICATION(application);
  GtkWindow *window =
      GTK_WINDOW(gtk_application_window_new(GTK_APPLICATION(application)));

  // Use a header bar when running in GNOME as this is the common style used
  // by applications and is the setup most users will be using (e.g. Ubuntu
  // desktop).
  // If running on X and not using GNOME then just use a traditional title bar
  // in case the window manager does more exotic layout, e.g. tiling.
  // If running on Wayland assume the header bar will work (may need changing
  // if future cases occur).
  gboolean use_header_bar = TRUE;
#ifdef GDK_WINDOWING_X11
  GdkScreen *screen = gtk_window_get_screen(window);
  if (GDK_IS_X11_SCREEN(screen)) {
    const gchar *wm_name = gdk_x11_screen_get_window_manager_name(screen);
    if (g_strcmp0(wm_name, "GNOME Shell") != 0) {
      use_header_bar = FALSE;
    }
  }
#endif
  if (use_header_bar) {
    GtkHeaderBar *header_bar = GTK_HEADER_BAR(gtk_header_bar_new());
    gtk_widget_show(GTK_WIDGET(header_bar));
    gtk_header_bar_set_title(header_bar, "ABK Desktop");
    gtk_header_bar_set_show_close_button(header_bar, TRUE);
    gtk_window_set_titlebar(window, GTK_WIDGET(header_bar));
  } else {
    gtk_window_set_title(window, "ABK Desktop");
  }

  gtk_window_set_default_size(window, 1280, 720);

  g_autoptr(FlDartProject) project = fl_dart_project_new();
  fl_dart_project_set_dart_entrypoint_arguments(
      project, self->dart_entrypoint_arguments);

  self->overlay = GTK_OVERLAY(gtk_overlay_new());
  gtk_widget_show(GTK_WIDGET(self->overlay));
  gtk_container_add(GTK_CONTAINER(window), GTK_WIDGET(self->overlay));

  self->view = fl_view_new(project);
  GdkRGBA background_color;
  // Background defaults to black, override it here if necessary, e.g. #00000000
  // for transparent.
  gdk_rgba_parse(&background_color, "#000000");
  fl_view_set_background_color(self->view, &background_color);
  gtk_widget_show(GTK_WIDGET(self->view));
  gtk_container_add(GTK_CONTAINER(self->overlay), GTK_WIDGET(self->view));

  // Show the window when Flutter renders.
  // Requires the view to be realized so we can start rendering.
  g_signal_connect_swapped(self->view, "first-frame",
                           G_CALLBACK(first_frame_cb), self);
  gtk_widget_realize(GTK_WIDGET(self->view));

  fl_register_plugins(FL_PLUGIN_REGISTRY(self->view));
  create_channels(self);

  gtk_widget_grab_focus(GTK_WIDGET(self->view));
}

// Implements GApplication::local_command_line.
static gboolean my_application_local_command_line(GApplication *application,
                                                  gchar ***arguments,
                                                  int *exit_status) {
  MyApplication *self = MY_APPLICATION(application);
  // Strip out the first argument as it is the binary name.
  self->dart_entrypoint_arguments = g_strdupv(*arguments + 1);

  g_autoptr(GError) error = nullptr;
  if (!g_application_register(application, nullptr, &error)) {
    g_warning("Failed to register: %s", error->message);
    *exit_status = 1;
    return TRUE;
  }

  g_application_activate(application);
  *exit_status = 0;

  return TRUE;
}

// Implements GApplication::startup.
static void my_application_startup(GApplication *application) {
  // MyApplication* self = MY_APPLICATION(object);

  // Perform any actions required at application startup.

  G_APPLICATION_CLASS(my_application_parent_class)->startup(application);
}

// Implements GApplication::shutdown.
static void my_application_shutdown(GApplication *application) {
  // MyApplication* self = MY_APPLICATION(object);

  // Perform any actions required at application shutdown.

  G_APPLICATION_CLASS(my_application_parent_class)->shutdown(application);
}

// Implements GObject::dispose.
static void my_application_dispose(GObject *object) {
  MyApplication *self = MY_APPLICATION(object);
  g_clear_pointer(&self->dart_entrypoint_arguments, g_strfreev);
  g_clear_pointer(&self->embedded_code_views, g_hash_table_unref);
  g_clear_object(&self->platform_channel);
  self->overlay = nullptr;
  G_OBJECT_CLASS(my_application_parent_class)->dispose(object);
}

static void my_application_class_init(MyApplicationClass *klass) {
  G_APPLICATION_CLASS(klass)->activate = my_application_activate;
  G_APPLICATION_CLASS(klass)->local_command_line =
      my_application_local_command_line;
  G_APPLICATION_CLASS(klass)->startup = my_application_startup;
  G_APPLICATION_CLASS(klass)->shutdown = my_application_shutdown;
  G_OBJECT_CLASS(klass)->dispose = my_application_dispose;
}

static void my_application_init(MyApplication *self) {
  self->view = nullptr;
  self->overlay = nullptr;
  self->platform_channel = nullptr;
  self->embedded_code_views = g_hash_table_new_full(
      g_str_hash, g_str_equal, g_free, embedded_code_view_free);
}

MyApplication *my_application_new() {
  // Set the program name to the application ID, which helps various systems
  // like GTK and desktop environments map this running application to its
  // corresponding .desktop file. This ensures better integration by allowing
  // the application to be recognized beyond its binary name.
  g_set_prgname(APPLICATION_ID);

  return MY_APPLICATION(g_object_new(my_application_get_type(),
                                     "application-id", APPLICATION_ID, "flags",
                                     G_APPLICATION_NON_UNIQUE, nullptr));
}
