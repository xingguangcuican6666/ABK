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
#include <cerrno>
#include <string>
#include <vector>
#include "abk_ksu.h"

static int fd = -1;
static uint32_t g_cached_app_profile_version = 0;
static constexpr uint32_t KSU_MIN_COMPAT_APP_PROFILE_VER = 2;

static bool is_supported_app_profile_version(uint32_t version) {
    return version >= KSU_MIN_COMPAT_APP_PROFILE_VER && version <= KSU_APP_PROFILE_VER;
}

static std::vector<uint32_t> build_app_profile_version_candidates(uint32_t preferred) {
    std::vector<uint32_t> versions;
    auto append_unique = [&versions](uint32_t version) {
        if (!is_supported_app_profile_version(version)) {
            return;
        }
        for (uint32_t existing : versions) {
            if (existing == version) {
                return;
            }
        }
        versions.push_back(version);
    };

    append_unique(preferred);
    append_unique(g_cached_app_profile_version);
    for (int version = static_cast<int>(KSU_APP_PROFILE_VER);
         version >= static_cast<int>(KSU_MIN_COMPAT_APP_PROFILE_VER);
         --version) {
        append_unique(static_cast<uint32_t>(version));
    }
    return versions;
}

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

static inline int ensure_driver_fd() {
    if (fd < 0) {
        fd = scan_driver_fd();
    }
    return fd;
}

template<typename... Args>
static int ksuctl(unsigned long op, Args &&... args) {
    static_assert(sizeof...(Args) <= 1, "ioctl expects at most one extra argument");

    int current_fd = ensure_driver_fd();
    if (current_fd < 0) {
        errno = ENODEV;
        return -1;
    }
    int ret = ioctl(current_fd, op, std::forward<Args>(args)...);
    if (ret < 0 && errno == EBADF) {
        fd = -1;
        current_fd = ensure_driver_fd();
        if (current_fd < 0) {
            errno = ENODEV;
            return -1;
        }
        ret = ioctl(current_fd, op, std::forward<Args>(args)...);
    }
    return ret;
}

static struct ksu_get_info_cmd g_version {};

struct ksu_get_info_cmd get_info() {
    if (!g_version.version) {
        if (ksuctl(KSU_IOCTL_GET_INFO, &g_version) < 0) {
            struct ksu_get_info_legacy_cmd legacy = {};
            if (ksuctl(KSU_IOCTL_GET_INFO_LEGACY, &legacy) == 0) {
                g_version.version = legacy.version;
                g_version.flags = legacy.flags;
                g_version.features = legacy.features;
                g_version.uapi_version = 0;
            }
        }
    }
    return g_version;
}

uint32_t get_version() {
    auto info = get_info();
    return info.version;
}

bool has_driver_fd() {
    return ensure_driver_fd() >= 0;
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

std::vector<uint32_t> get_allow_list_uids() {
    struct ksu_new_get_allow_list_cmd probe = {
            .count = 0
    };
    if (!get_allow_list(&probe) || probe.total_count == 0) {
        return {};
    }

    const size_t total_count = probe.total_count;
    std::vector<uint8_t> storage(
            sizeof(struct ksu_new_get_allow_list_cmd) + total_count * sizeof(uint32_t),
            0
    );
    auto *cmd = reinterpret_cast<struct ksu_new_get_allow_list_cmd *>(storage.data());
    cmd->count = static_cast<__u16>(total_count);
    if (!get_allow_list(cmd)) {
        return {};
    }

    const size_t actual_count = cmd->count < cmd->total_count ? cmd->count : cmd->total_count;
    return std::vector<uint32_t>(cmd->uids, cmd->uids + actual_count);
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
    if (!profile) {
        errno = EINVAL;
        return false;
    }

    const auto versions = build_app_profile_version_candidates(profile->version);
    int last_errno = errno;
    for (uint32_t version : versions) {
        struct ksu_set_app_profile_cmd cmd = {};
        cmd.profile = *profile;
        cmd.profile.version = version;
        if (ksuctl(KSU_IOCTL_SET_APP_PROFILE, &cmd) == 0) {
            g_cached_app_profile_version = version;
            return true;
        }
        last_errno = errno;
        if (errno == ENODEV || errno == EBADF) {
            break;
        }
    }

    errno = last_errno;
    return false;
}

int get_app_profile(app_profile *profile) {
    struct ksu_get_app_profile_cmd cmd = {.profile = *profile};
    int ret = ksuctl(KSU_IOCTL_GET_APP_PROFILE, &cmd);
    *profile = cmd.profile;
    if (ret == 0 && is_supported_app_profile_version(profile->version)) {
        g_cached_app_profile_version = profile->version;
    }
    return ret;
}

static inline bool set_feature(uint32_t feature_id, uint64_t value);

bool set_su_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_SU_COMPAT, enabled ? 1 : 0);
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
    for (int attempt = 0; attempt < 3; attempt++) {
        errno = 0;
        if (ksuctl(KSU_IOCTL_SET_FEATURE, &cmd) == 0) {
            return true;
        }
        if (errno != EAGAIN) {
            return false;
        }
        usleep(20 * 1000);
    }
    return false;
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
