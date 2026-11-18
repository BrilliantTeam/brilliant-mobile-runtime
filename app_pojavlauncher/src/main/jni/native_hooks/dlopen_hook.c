#include "environ/environ.h"
#include "utils.h"
#include "native_hooks.h"
#include "log.h"

#include <bytehook.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
// WARNING: Hooking dlopen and dlsym does not work for all devices, seems to be a conflict with
// the turnip loader, perhaps Dobby would fare better.
typedef void *(*dlopen_func_t)(const char *, int);
typedef void *(*dlsym_func_t)(void *, const char *);
typedef jint (*JNI_OnLoad_t)(JavaVM *vm, void *reserved);

typedef struct {
    const char *library_name;
    const char *library_rename;
    void *handle;
    JNI_OnLoad_t jni_onload;
} LibraryRedirect;

static LibraryRedirect libRedirectInfo[] = {
        { "libSDL3.so", "libSDL3.so" },
        { "libSDL2.so", "libSDL2.so" },
        { NULL, NULL } // Null termination
};

__attribute__((constructor)) static void init() {
    // We can't hook dlopen so we make do with this.
    libRedirectInfo[0].handle = dlopen("libSDL3.so", RTLD_LOCAL | RTLD_LAZY);
    libRedirectInfo[1].handle = dlopen("libSDL2.so", RTLD_LOCAL | RTLD_LAZY);
}

// Loose matching of lib file name to hard coded list
static LibraryRedirect* get_library_redirect_if_needed(const char *filename) {
    if (filename == NULL)
        return NULL;
    const char *basename = strrchr(filename, '/');
    basename = basename ? basename + 1 : filename;
    for (size_t i = 0; libRedirectInfo[i].library_name != NULL; ++i) {
        if (strstr(basename, libRedirectInfo[i].library_name) != NULL) {
            return &libRedirectInfo[i];
        }
    }
    return NULL;
}

void *custom_dlopen(const char *filename, int flags) {
    LibraryRedirect *redirect = get_library_redirect_if_needed(filename);
    const char *target_filename = filename;
    if (redirect != NULL) {
        target_filename = redirect->library_rename;
        LOGI("Redirecting dlopen: %s -> %s", filename, target_filename);
    }

    void *result = BYTEHOOK_CALL_PREV(
            custom_dlopen,
            dlopen_func_t,
            target_filename,
            flags);

    if (redirect != NULL)
        redirect->handle = result;

    BYTEHOOK_POP_STACK();
    return result;
}

static LibraryRedirect *get_redirect_by_handle(void *handle) {
    if (handle == NULL)
        return NULL;

    for (size_t i = 0; libRedirectInfo[i].library_name != NULL; ++i) {
        if (libRedirectInfo[i].handle == handle)
            return &libRedirectInfo[i];
    }

    return NULL;
}

static jint rerouted_jni_onload(
        LibraryRedirect *redirect,
        JavaVM *vm,
        void *reserved) {

    if (redirect == NULL) {
        return JNI_ERR;
    }

    if (pojav_environ != NULL &&
            pojav_environ->dalvikJavaVMPtr == vm &&
            redirect->jni_onload != NULL) {
        return ((JNI_OnLoad_t) redirect->jni_onload)(vm, reserved);
    }

    return JNI_VERSION_1_4;
}

#define DEFINE_JNI_WRAPPER(id)                                              \
    static jint jni_onload_##id(                                            \
            JavaVM *vm,                                                     \
            void *reserved) {                                               \
        return rerouted_jni_onload(&libRedirectInfo[id], vm, reserved);     \
    }

// This is a bit magicky yeah
DEFINE_JNI_WRAPPER(0)
DEFINE_JNI_WRAPPER(1)

static JNI_OnLoad_t get_jni_onload_wrapper(
        LibraryRedirect *redirect) {
    if (redirect == &libRedirectInfo[0]) {
        return jni_onload_0;
    }

    if (redirect == &libRedirectInfo[1]) {
        return jni_onload_1;
    }

    return NULL;
}

typedef void *(*sdl_create_window_t)(const char *, int, int, uint64_t);
typedef void *(*sdl_create_window_props_t)(uint32_t);
typedef void (*sdl_destroy_window_t)(void *);

static sdl_create_window_t real_create_window;
static sdl_create_window_props_t real_create_window_props;
static sdl_destroy_window_t real_destroy_window;
static void *shared_window;
static int shared_window_refs;

static void *share_window(void *window) {
    if (window != NULL) {
        shared_window = window;
        shared_window_refs = 1;
    }
    return window;
}

static void *proxy_create_window(const char *title, int w, int h, uint64_t flags) {
    if (shared_window != NULL) {
        shared_window_refs++;
        return shared_window;
    }
    return share_window(real_create_window(title, w, h, flags));
}

static void *proxy_create_window_props(uint32_t props) {
    if (shared_window != NULL) {
        shared_window_refs++;
        return shared_window;
    }
    return share_window(real_create_window_props(props));
}

static void proxy_destroy_window(void *window) {
    if (window != NULL && window == shared_window) {
        if (--shared_window_refs > 0) return;
        shared_window = NULL;
    }
    real_destroy_window(window);
}

typedef bool (*sdl_start_text_t)(void *);
typedef bool (*sdl_start_text_props_t)(void *, uint32_t);
typedef bool (*sdl_stop_text_t)(void *);

static sdl_start_text_t real_start_text;
static sdl_start_text_props_t real_start_text_props;
static sdl_stop_text_t real_stop_text;

static void notify_text_input(int action) {
    TRY_ATTACH_ENV(dvm_env, pojav_environ->dalvikJavaVMPtr, "Text input notify failed", return;);
    notifyLauncher(dvm_env, NOTIF_TYPE_SDL, (int[]){action}, 1);
}

static bool proxy_start_text(void *window) {
    bool r = real_start_text(window);
    notify_text_input(ACTION_START_TEXT_INPUT);
    return r;
}

static bool proxy_start_text_props(void *window, uint32_t props) {
    bool r = real_start_text_props(window, props);
    notify_text_input(ACTION_START_TEXT_INPUT);
    return r;
}

static bool proxy_stop_text(void *window) {
    bool r = real_stop_text(window);
    notify_text_input(ACTION_STOP_TEXT_INPUT);
    return r;
}

static bool proxy_open_url(const char *url) {
    if (url == NULL) return false;
    TRY_ATTACH_ENV(dvm_env, pojav_environ->dalvikJavaVMPtr, "Open URL failed", return false;);
    jstring link = (*dvm_env)->NewStringUTF(dvm_env, url);
    (*dvm_env)->CallStaticObjectMethod(dvm_env, pojav_environ->bridgeClazz, pojav_environ->method_accessAndroidClipboard, CLIPBOARD_OPEN, link);
    (*dvm_env)->DeleteLocalRef(dvm_env, link);
    return true;
}

static void *sdl_window_proxy(const char *symbol, void *real) {
    if (real == NULL) return NULL;
    if (strcmp(symbol, "SDL_OpenURL") == 0) {
        return (void *) &proxy_open_url;
    }
    if (strcmp(symbol, "SDL_StartTextInput") == 0) {
        real_start_text = (sdl_start_text_t) real;
        return (void *) &proxy_start_text;
    }
    if (strcmp(symbol, "SDL_StartTextInputWithProperties") == 0) {
        real_start_text_props = (sdl_start_text_props_t) real;
        return (void *) &proxy_start_text_props;
    }
    if (strcmp(symbol, "SDL_StopTextInput") == 0) {
        real_stop_text = (sdl_stop_text_t) real;
        return (void *) &proxy_stop_text;
    }
    if (strcmp(symbol, "SDL_CreateWindow") == 0) {
        real_create_window = (sdl_create_window_t) real;
        return (void *) &proxy_create_window;
    }
    if (strcmp(symbol, "SDL_CreateWindowWithProperties") == 0) {
        real_create_window_props = (sdl_create_window_props_t) real;
        return (void *) &proxy_create_window_props;
    }
    if (strcmp(symbol, "SDL_DestroyWindow") == 0) {
        real_destroy_window = (sdl_destroy_window_t) real;
        return (void *) &proxy_destroy_window;
    }
    return real;
}

typedef unsigned int (*egl_make_current_t)(void *, void *, void *, void *);
typedef void *(*egl_get_current_context_t)(void);

static egl_make_current_t real_egl_make_current;
static egl_get_current_context_t real_egl_get_current_context;

static unsigned int proxy_egl_make_current(void *display, void *draw, void *read, void *context) {
    if (context == NULL && draw == NULL && read == NULL) {
        void *current = real_egl_get_current_context();
        if (current != NULL && real_egl_make_current(display, NULL, NULL, current)) return 1;
    }
    return real_egl_make_current(display, draw, read, context);
}

static bool called_from_sdl3(void *return_address) {
    Dl_info info;
    return dladdr(return_address, &info) && info.dli_fname != NULL && strstr(info.dli_fname, "libSDL3") != NULL;
}

void *custom_dlsym(void *handle, const char *symbol) {
    void *result = BYTEHOOK_CALL_PREV(
            custom_dlsym,
            dlsym_func_t,
            handle,
            symbol);
    if (result != NULL && symbol != NULL && strcmp(symbol, "eglMakeCurrent") == 0 && called_from_sdl3(BYTEHOOK_RETURN_ADDRESS())) {
        void *get_context = BYTEHOOK_CALL_PREV(custom_dlsym, dlsym_func_t, handle, "eglGetCurrentContext");
        if (get_context != NULL) {
            real_egl_make_current = (egl_make_current_t) result;
            real_egl_get_current_context = (egl_get_current_context_t) get_context;
            LOGI("Brilliant: keeping SDL GL context current while paused");
            BYTEHOOK_POP_STACK();
            return (void *) &proxy_egl_make_current;
        }
    }
    LibraryRedirect *redirect = get_redirect_by_handle(handle);
    if (redirect != NULL && symbol != NULL && strncmp(symbol, "SDL_", 4) == 0 && strstr(redirect->library_name, "libSDL3")) {
        void *proxy = sdl_window_proxy(symbol, result);
        if (proxy != result) LOGI("Brilliant: sharing single SDL window via %s", symbol);
        BYTEHOOK_POP_STACK();
        return proxy;
    }
    if ((redirect != NULL) && strcmp(symbol, "JNI_OnLoad") == 0) {
        if (strstr(redirect->library_name, "libSDL3")) {
            redirect->jni_onload = (JNI_OnLoad_t) result;
            // This outputs in the minecraft logs
            LOGI("Brilliant: Intercepted dlsym on SDL3 JNI_OnLoad: %p", result);
            BYTEHOOK_POP_STACK();
            return get_jni_onload_wrapper(redirect);
        }
    }
    BYTEHOOK_POP_STACK();
    return result;
}

void create_dlopen_hooks(bytehook_hook_all_t bytehook_hook_all_p) {
    // FIXME: Hooking dlopen causes a crash with Turnip loader, so let's stop doing that entirely
    // Maybe using android_dlopen_ext would help but I can't be bothered to do that yet
    bytehook_stub_t stub_dlopen = (void *)(uintptr_t)0xD15AB1ED;
//            bytehook_hook_all_p(NULL, "dlopen", &custom_dlopen, NULL, NULL);
    bytehook_stub_t stub_dlsym =
            bytehook_hook_all_p(NULL, "dlsym", &custom_dlsym, NULL, NULL);
    LOGI("Successfully initialized dlopen hooks, stub: %p %p", stub_dlopen, stub_dlsym);
}