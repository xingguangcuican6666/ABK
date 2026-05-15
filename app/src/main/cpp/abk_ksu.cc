//
// Created by weishu on 2022/12/9.
//

#include <sys/prctl.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <utility>
#include <android/log.h>
#include <dirent.h>
#include <cstdlib>

#include <unistd.h>
#include <climits>
#include <sys/syscall.h>
#include <cerrno>
#include <string>
#include <vector>
#include "abk_ksu.h"

#ifndef SYS_reboot
#define SYS_reboot __NR_reboot
#endif

static int fd = -1;

static inline int scan_driver_fd() {
    const char *kName = "[ksu_driver]";
    DIR *dir = opendir("/proc/self/fd");
    if (!dir) {
        return -1;
    }

    int found = -1;
    struct dirent *de;
    char path[64];
    char target[PATH_MAX];

    while ((de = readdir(dir)) != NULL) {
        if (de->d_name[0] == '.') {
            continue;
        }

        char *endptr = NULL;
        long fd_long = strtol(de->d_name, &endptr, 10);
        if (!de->d_name[0] || *endptr != '\0' || fd_long < 0 || fd_long > INT_MAX) {
            continue;
        }

        snprintf(path, sizeof(path), "/proc/self/fd/%s", de->d_name);
        ssize_t n = readlink(path, target, sizeof(target) - 1);
        if (n < 0) {
            continue;
        }
        target[n] = '\0';

        const char *base = strrchr(target, '/');
        base = base ? base + 1 : target;

        if (strstr(base, kName)) {
            found = (int)fd_long;
            break;
        }
    }

    closedir(dir);
    return found;
}

static inline int install_driver_fd() {
    int installed_fd = -1;

    syscall(SYS_reboot, KSU_INSTALL_MAGIC1, KSU_INSTALL_MAGIC2, 0, &installed_fd);
    if (installed_fd >= 0) {
        return installed_fd;
    }

    return scan_driver_fd();
}

static inline int ensure_driver_fd() {
    if (fd < 0) {
        fd = scan_driver_fd();
    }
    if (fd < 0) {
        fd = install_driver_fd();
    }
    return fd;
}

template<typename... Args>
static int ksuctl(unsigned long op, Args &&... args) {
    static_assert(sizeof...(Args) <= 1, "ioctl expects at most one extra argument");

    int current_fd = ensure_driver_fd();
    int ret = ioctl(current_fd, op, std::forward<Args>(args)...);
    if (ret < 0 && errno == EBADF) {
        fd = -1;
        current_fd = ensure_driver_fd();
        ret = ioctl(current_fd, op, std::forward<Args>(args)...);
    }
    return ret;
}

static struct ksu_get_info_cmd g_version {};

struct ksu_get_info_cmd get_info() {
    if (!g_version.version) {
        ksuctl(KSU_IOCTL_GET_INFO, &g_version);
    }
    return g_version;
}

uint32_t get_version() {
    auto info = get_info();
    return info.version;
}

void get_full_version(char *buff, size_t size) {
    if (!buff || size == 0) {
        return;
    }
    buff[0] = '\0';
    struct ksu_get_full_version_cmd cmd = {};
    if (ksuctl(KSU_IOCTL_GET_FULL_VERSION, &cmd) == 0) {
        strncpy(buff, cmd.version_full, size - 1);
        buff[size - 1] = '\0';
    }
}

void get_hook_type(char *buff, size_t size) {
    if (!buff || size == 0) {
        return;
    }
    buff[0] = '\0';
    struct ksu_hook_type_cmd cmd = {};
    if (ksuctl(KSU_IOCTL_HOOK_TYPE, &cmd) == 0) {
        strncpy(buff, cmd.hook_type, size - 1);
        buff[size - 1] = '\0';
    }
}

bool get_allow_list(struct ksu_new_get_allow_list_cmd *cmd) {
    return ksuctl(KSU_IOCTL_NEW_GET_ALLOW_LIST, cmd) == 0;
}

bool is_safe_mode() {
    struct ksu_check_safemode_cmd cmd = {};
    ksuctl(KSU_IOCTL_CHECK_SAFEMODE, &cmd);
    return cmd.in_safe_mode;
}

bool is_lkm_mode() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_LKM) != 0;
    }
    return (legacy_get_info().second & KSU_GET_INFO_FLAG_LKM) != 0;
}

bool is_late_load_mode() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_LATE_LOAD) != 0;
    }
    return false;
}

bool is_manager() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_MANAGER) != 0;
    }
    return legacy_get_info().first > 0;
}

bool is_pr_build() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_PR_BUILD) != 0;
    }
    return false;
}

bool uid_should_umount(int uid) {
    struct ksu_uid_should_umount_cmd cmd = {};
    cmd.uid = uid;
    ksuctl(KSU_IOCTL_UID_SHOULD_UMOUNT, &cmd);
    return cmd.should_umount;
}

bool set_app_profile(const app_profile *profile) {
    struct ksu_set_app_profile_cmd cmd = {};
    cmd.profile = *profile;
    return ksuctl(KSU_IOCTL_SET_APP_PROFILE, &cmd) == 0;
}

int get_app_profile(app_profile *profile) {
    struct ksu_get_app_profile_cmd cmd = {.profile = *profile};
    int ret = ksuctl(KSU_IOCTL_GET_APP_PROFILE, &cmd);
    *profile = cmd.profile;
    return ret;
}

bool set_su_enabled(bool enabled) {
    struct ksu_set_feature_cmd cmd = {};
    cmd.feature_id = KSU_FEATURE_SU_COMPAT;
    cmd.value = enabled ? 1 : 0;
    return ksuctl(KSU_IOCTL_SET_FEATURE, &cmd) == 0;
}

bool is_su_enabled() {
    struct ksu_get_feature_cmd cmd = {};
    cmd.feature_id = KSU_FEATURE_SU_COMPAT;
    if (ksuctl(KSU_IOCTL_GET_FEATURE, &cmd) != 0) {
        return false;
    }
    if (!cmd.supported) {
        return false;
    }
    return cmd.value != 0;
}

static inline bool get_feature(uint32_t feature_id, uint64_t *out_value, bool *out_supported) {
    struct ksu_get_feature_cmd cmd = {};
    cmd.feature_id = feature_id;
    if (ksuctl(KSU_IOCTL_GET_FEATURE, &cmd) != 0) {
        return false;
    }
    if (out_value) *out_value = cmd.value;
    if (out_supported) *out_supported = cmd.supported;
    return true;
}

static inline bool set_feature(uint32_t feature_id, uint64_t value) {
    struct ksu_set_feature_cmd cmd = {};
    cmd.feature_id = feature_id;
    cmd.value = value;
    return ksuctl(KSU_IOCTL_SET_FEATURE, &cmd) == 0;
}

bool set_kernel_umount_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_KERNEL_UMOUNT, enabled ? 1 : 0);
}

bool is_kernel_umount_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_KERNEL_UMOUNT, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool is_sulog_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_SULOG, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool set_sulog_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_SULOG, enabled ? 1 : 0);
}

bool get_feature_state(uint32_t feature_id, uint64_t *out_value, bool *out_supported) {
    return get_feature(feature_id, out_value, out_supported);
}

int set_selinux_hide_enabled(bool enabled) {
    if (!set_feature(KSU_FEATURE_SELINUX_HIDE, enabled ? 1 : 0)) {
        return -errno;
    }
    return 0;
}

bool is_selinux_hide_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_SELINUX_HIDE, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool abk_control_get_status(std::string *out) {
    if (!out) {
        return false;
    }

    struct abk_control_status_cmd cmd = {};
    int ret = ksuctl(ABK_CONTROL_IOCTL_GET_STATUS, &cmd);
    if (ret != 0 && errno != ENOSPC) {
        return false;
    }
    if (cmd.data_len == 0 || cmd.data_len > 1024 * 1024) {
        return false;
    }

    std::vector<char> buffer(static_cast<size_t>(cmd.data_len) + 1, '\0');
    cmd.data = reinterpret_cast<uintptr_t>(buffer.data());
    cmd.data_len = buffer.size();

    if (ksuctl(ABK_CONTROL_IOCTL_GET_STATUS, &cmd) != 0) {
        return false;
    }

    size_t len = static_cast<size_t>(cmd.data_len);
    if (len >= buffer.size()) {
        len = buffer.size() - 1;
    }
    out->assign(buffer.data(), strnlen(buffer.data(), len));
    return true;
}

bool abk_control_run_command(const char *command) {
    if (!command) {
        return false;
    }

    size_t len = strnlen(command, ABK_CONTROL_MAX_COMMAND + 1);
    if (len == 0 || len > ABK_CONTROL_MAX_COMMAND) {
        return false;
    }

    struct abk_control_command_cmd cmd = {};
    cmd.command_len = len;
    cmd.command = reinterpret_cast<uintptr_t>(command);
    return ksuctl(ABK_CONTROL_IOCTL_RUN_COMMAND, &cmd) == 0;
}
