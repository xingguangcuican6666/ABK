#include <linux/binfmts.h>
#include <linux/blkdev.h>
#include <linux/blkpg.h>
#include <linux/atomic.h>
#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/elf.h>
#include <linux/errno.h>
#include <linux/fs.h>
#include <linux/init.h>
#include <linux/kernel_read_file.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 11, 0)
#include <linux/genhd.h>
#endif
#include <linux/jiffies.h>
#include <linux/kernel.h>
#include <linux/kobject.h>
#include <linux/mm.h>
#include <linux/mman.h>
#include <linux/module.h>
#include <linux/overflow.h>
#include <linux/ptrace.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/security.h>
#include <linux/sizes.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/sysfs.h>
#include <linux/uaccess.h>
#include <linux/uio.h>
#include <linux/workqueue.h>
#include <linux/xingguang_ddk.h>
#include <uapi/linux/blkzoned.h>

#include "kernel_compat.h"

#define XG_TAG "Xingguang_DDK"

#define XG_WRITE_SAMPLE_BYTES 256U
#define XG_EXEC_PATH_LOG_BYTES 192U
#define XG_EXEC_SAMPLE_BYTES 2048U
#define XG_GZEXE_CHAIN_WINDOW_MS 60000U
#define XG_POSTFS_SANDBOX_WINDOW_MS (5U * 60U * 1000U)
#define XG_WATCHDOG_INTERVAL_MS 5000U
#define XG_WATCHDOG_CANARY_A 0x58474444574d4441ULL
#define XG_WATCHDOG_CANARY_B 0xa7b8bbc3a8b2bbb9ULL
#define XG_DELAYED_ENFORCE_MS (5U * 60U * 1000U)
#define XG_OTA_AUDIT_DELAY_MS (15U * 60U * 1000U)
#define XG_EARLY_WINDOW_BYTES (1024U * 1024U)
#define XG_SINGLE_DEV_EARLY_LIMIT (256U * 1024U)
#define XG_MULTI_DEV_EARLY_LIMIT 2U
#define XG_FLOW_WINDOW_MS 10000U
#define XG_GENERIC_BULK_WRITE_LIMIT (8U * 1024U * 1024U)
#define XG_RADIO_NV_PROFILE_SLOTS 8U
#define XG_RADIO_NV_PROFILE_TOLERANCE_BYTES (1024U * 1024U)
#define XG_RADIO_NV_PROFILE_MIN_COUNT 2U

#define XG_GZEXE_SEEN_MAGIC (1U << 0)
#define XG_GZEXE_SEEN_SKIP (1U << 1)
#define XG_GZEXE_SEEN_DECOMPRESS (1U << 2)
#define XG_GZEXE_SEEN_TMP (1U << 3)

enum xg_part_class {
	XG_PART_GENERIC,
	XG_PART_FIRMWARE,
	XG_PART_RADIO_NV,
};

enum xg_boot_stage {
	XG_STAGE_EARLY,
	XG_STAGE_BOOT_AUDIT,
	XG_STAGE_ENFORCED,
};

struct xg_exec_features {
	bool elf;
	bool elf_aarch64;
	bool elf_no_sections;
	bool elf_packed_layout;
	bool gzexe;
	bool fd_empty_path;
	bool trusted_system_shell;
};

struct xg_write_flow {
	pid_t tgid;
	unsigned long last_seen;
	dev_t devs[4];
	u64 early_bytes[4];
	unsigned int dev_count;
};

struct xg_tagged_task {
	pid_t tgid;
	u32 sid;
	unsigned long last_seen;
};

struct xg_radio_nv_profile {
	dev_t dev;
	u64 min_pos;
	u64 max_end;
	u64 max_count;
	unsigned int writes;
	bool valid;
};

struct xg_trusted_exec_identity {
	const char *path;
	dev_t sb_dev;
	unsigned long ino;
	loff_t size;
	umode_t mode;
	bool valid;
};

struct xg_selinux_task_security {
	u32 osid;
	u32 sid;
	u32 exec_sid;
	u32 create_sid;
	u32 keycreate_sid;
	u32 sockcreate_sid;
};

static bool xg_block_setenforce = true;
static bool xg_exec_guard_all_uid = true;
static bool xg_strict_shell_exec_guard = true;
static bool xg_block_disguised_shell_exec = true;
static bool xg_block_gzexe_exec = true;
static bool xg_block_packed_elf_exec = true;
static bool xg_block_shell_writable_scripts = true;

static DEFINE_SPINLOCK(xg_dynamic_lock);
static DEFINE_SPINLOCK(xg_flow_lock);
static DEFINE_SPINLOCK(xg_shell_polluted_lock);
static DEFINE_SPINLOCK(xg_gzexe_polluted_lock);
static DEFINE_SPINLOCK(xg_postfs_sandbox_lock);
static DEFINE_SPINLOCK(xg_trusted_shell_lock);
static DEFINE_SPINLOCK(xg_watchdog_lock);
static DEFINE_SPINLOCK(xg_radio_nv_profile_lock);

static atomic_t xg_stage = ATOMIC_INIT(XG_STAGE_EARLY);
static struct delayed_work xg_watchdog_work;
static struct delayed_work xg_delayed_enforce_work;
static struct kobject *xg_sysfs_kobj;
static struct xg_tagged_task xg_dynamic_tasks[32];
static struct xg_write_flow xg_flows[32];
static struct xg_tagged_task xg_shell_polluted_tasks[16];
static struct xg_tagged_task xg_gzexe_polluted_tasks[32];
static struct xg_tagged_task xg_postfs_sandbox_tasks[32];
static struct xg_radio_nv_profile
	xg_radio_nv_profiles[XG_RADIO_NV_PROFILE_SLOTS];
static atomic_t xg_delayed_enforce_ready = ATOMIC_INIT(0);
static atomic_t xg_delayed_enforced = ATOMIC_INIT(0);
static atomic_t xg_ota_audit_delay_seen = ATOMIC_INIT(0);
static bool xg_watchdog_tripped;
static u64 xg_watchdog_canary_a = XG_WATCHDOG_CANARY_A;
static u64 xg_watchdog_canary_b = XG_WATCHDOG_CANARY_B;

static const char *const xg_firmware_partitions[] = {
    "abl",	   "aop",	    "aop_config",
    "bluetooth",   "boot",	    "cpucp",
    "cpucp_dtb",   "devcfg",	    "dsp",
    "dtbo",	   "featenabler",   "hyp",
    "imagefv",	   "init_boot",	    "keymaster",
    "modem",	   "modemfirmware", "multiimgoem",
    "multiimgqti", "qupfw",	    "recovery",
    "shrm",	   "spuservice",    "super",
    "tz",	   "uefi",	    "uefisecapp",
    "vbmeta",	   "vbmeta_system", "vbmeta_vendor",
    "vendor_boot", "vm-bootsys",    "xbl",
    "xbl_config",
};

static const char *const xg_radio_nv_partitions[] = {
    "fsc",
    "fsg",
    "modemst1",
    "modemst2",
};

static struct xg_trusted_exec_identity xg_trusted_shells[] = {
    {.path = "/system/bin/sh"}, {.path = "/system/bin/mksh"},
    {.path = "/vendor/bin/sh"}, {.path = "/product/bin/sh"},
    {.path = "/odm/bin/sh"},
};

static const char *xg_stage_name(enum xg_boot_stage stage)
{
	switch (stage) {
	case XG_STAGE_EARLY:
		return "EARLY";
	case XG_STAGE_BOOT_AUDIT:
		return "BOOT_AUDIT";
	case XG_STAGE_ENFORCED:
		return "ENFORCED";
	default:
		return "unknown";
	}
}

static enum xg_boot_stage xg_current_stage(void)
{
	int stage = atomic_read(&xg_stage);

	if (stage < XG_STAGE_EARLY)
		return XG_STAGE_EARLY;
	if (stage > XG_STAGE_ENFORCED)
		return XG_STAGE_ENFORCED;
	return (enum xg_boot_stage)stage;
}

static bool xg_stage_at_least(enum xg_boot_stage stage)
{
	return xg_current_stage() >= stage;
}

static bool xg_stage_allows_exec_policy(void)
{
	return xg_stage_at_least(XG_STAGE_BOOT_AUDIT);
}

static bool xg_policy_is_enforced(void)
{
	return xg_stage_at_least(XG_STAGE_ENFORCED);
}

static bool xg_current_postfs_sandboxed(const char **reason);

static bool xg_policy_subject_can_block(void)
{
	return xg_policy_is_enforced() || xg_current_postfs_sandboxed(NULL);
}

static bool xg_policy_can_block(const char *reason)
{
	return reason && *reason && xg_policy_subject_can_block();
}

static int xg_policy_deny(const char *reason, int err)
{
	return xg_policy_can_block(reason) ? -err : 0;
}

static bool xg_dm_target_is_snapshot_ota(const char *type)
{
	return type && (!strcmp(type, "snapshot") ||
			!strcmp(type, "snapshot-merge") ||
			!strcmp(type, "snapshot-origin") ||
			!strcmp(type, "user"));
}

static void xg_extend_audit_for_snapshot_ota(const char *type)
{
	if (!xg_dm_target_is_snapshot_ota(type) || xg_policy_is_enforced())
		return;

	if (atomic_cmpxchg(&xg_ota_audit_delay_seen, 0, 1) == 0)
		pr_warn_ratelimited(
		    XG_TAG ": snapshot/COW dm target detected, extending "
			   "boot audit window target=%s delay_ms=%u\n",
		    type ? type : "", XG_OTA_AUDIT_DELAY_MS);

	if (atomic_read(&xg_delayed_enforce_ready) &&
	    !atomic_read(&xg_delayed_enforced))
		mod_delayed_work(system_wq, &xg_delayed_enforce_work,
				 msecs_to_jiffies(XG_OTA_AUDIT_DELAY_MS));
}

void xg_ddk_dm_target_add(const char *type)
{
	xg_extend_audit_for_snapshot_ota(type);
}
EXPORT_SYMBOL_GPL(xg_ddk_dm_target_add);

static void xg_advance_stage(enum xg_boot_stage next, const char *reason,
			     const char *path)
{
	enum xg_boot_stage old;
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	for (;;) {
		old = xg_current_stage();
		if (old >= next)
			return;
		if (atomic_cmpxchg(&xg_stage, old, next) == old)
			break;
	}

	pr_warn_ratelimited(XG_TAG ": stage %s -> %s reason=%s pid=%d uid=%u "
				   "comm=%s path=%.*s\n",
			    xg_stage_name(old), xg_stage_name(next),
			    reason ? reason : "unspecified", current->pid,
			    __kuid_val(current_uid()), current->comm,
			    (int)path_len, path ? path : "");
}

static void xg_enter_enforced(const char *reason, const char *path)
{
	xg_advance_stage(XG_STAGE_ENFORCED, reason, path);
}

static bool xg_mem_has(const u8 *buf, size_t len, const char *needle)
{
	size_t needle_len = strlen(needle);
	size_t i;

	if (!buf || !needle_len || len < needle_len)
		return false;

	for (i = 0; i <= len - needle_len; i++) {
		if (!memcmp(buf + i, needle, needle_len))
			return true;
	}

	return false;
}

static const char *xg_basename(const char *path)
{
	const char *base = path;
	size_t i;

	if (!path)
		return "";

	for (i = 0; i < XG_EXEC_PATH_LOG_BYTES && path[i]; i++) {
		if (path[i] == '/')
			base = path + i + 1;
	}

	return base;
}

static bool xg_name_eq(const char *name, const char *expected)
{
	size_t expected_len = strlen(expected);

	return name && strnlen(name, expected_len + 1) == expected_len &&
	       !memcmp(name, expected, expected_len);
}

static bool xg_comm_eq(const char *expected)
{
	return xg_name_eq(current->comm, expected);
}

static bool xg_comm_has_prefix(const char *prefix)
{
	return !strncmp(current->comm, prefix, strlen(prefix));
}

static bool xg_name_has_suffix(const char *name, const char *suffix)
{
	size_t name_len;
	size_t suffix_len = strlen(suffix);

	if (!name)
		return false;

	name_len = strnlen(name, XG_EXEC_PATH_LOG_BYTES);
	if (name_len < suffix_len)
		return false;

	return !memcmp(name + name_len - suffix_len, suffix, suffix_len);
}

static bool xg_basename_is_shell_like(const char *base)
{
	return xg_name_eq(base, "sh") || xg_name_eq(base, "mksh") ||
	       xg_name_eq(base, "ash") || xg_name_eq(base, "bash") ||
	       xg_name_eq(base, "zsh") || xg_name_eq(base, "dash") ||
	       xg_name_eq(base, "ksh") || xg_name_eq(base, "fish");
}

static bool xg_current_is_shell_like(void)
{
	return xg_comm_eq("sh") || xg_comm_eq("mksh") || xg_comm_eq("ash") ||
	       xg_comm_eq("bash") || xg_comm_eq("zsh") || xg_comm_eq("dash") ||
	       xg_comm_eq("ksh") || xg_comm_has_prefix("fish") ||
	       xg_comm_eq("toybox");
}

static bool xg_current_is_script_reader_like(void)
{
	return xg_current_is_shell_like() || xg_comm_eq("tail") ||
	       xg_comm_eq("gzip") || xg_comm_eq("gunzip") ||
	       xg_comm_eq("zcat") || xg_comm_eq("busybox") ||
	       xg_comm_eq("cat") || xg_comm_eq("head") || xg_comm_eq("sed") ||
	       xg_comm_eq("dd");
}

static bool xg_path_has_prefix(const char *path, const char *prefix)
{
	return path && !strncmp(path, prefix, strlen(prefix));
}

static bool xg_path_under_dir(const char *path, const char *dir)
{
	size_t dir_len;

	if (!path || !dir)
		return false;

	dir_len = strlen(dir);
	return !strncmp(path, dir, dir_len) &&
	       (path[dir_len] == '\0' || path[dir_len] == '/');
}

static bool xg_path_has_component(const char *path, const char *component)
{
	const char *p;
	size_t len;

	if (!path || !component)
		return false;

	len = strlen(component);
	p = path;
	while ((p = strstr(p, component))) {
		char before = p == path ? '/' : p[-1];
		char after = p[len];

		if ((before == '/' || before == '\0') &&
		    (after == '/' || after == '\0' || after == ':' ||
		     after == '.'))
			return true;
		p += len;
	}

	return false;
}

static bool xg_name_is_post_fs_data_script(const char *name)
{
	return xg_name_eq(name, "post-fs-data") ||
	       xg_name_eq(name, "post-fs-data.sh");
}

static bool xg_path_is_post_fs_data_related(const char *path)
{
	if (!xg_name_is_post_fs_data_script(xg_basename(path)))
		return false;

	return xg_path_under_dir(path, "/data/adb/modules") ||
	       xg_path_under_dir(path, "/data/adb/modules_update");
}

static void xg_mark_current_postfs_sandbox(const char *reason,
					   const char *path);

static bool xg_name_is_root_manager_token(const char *name)
{
	return xg_name_eq(name, "su") || xg_name_eq(name, "ksu") ||
	       xg_name_eq(name, "ksud") || xg_name_eq(name, "magisk") ||
	       xg_name_eq(name, "magiskd") || xg_name_eq(name, "KernelSU") ||
	       xg_name_eq(name, "kernelsu") || xg_name_eq(name, "KSU") ||
	       xg_name_eq(name, "SukiSU") || xg_name_eq(name, "sukisu");
}

static bool xg_path_is_root_manager_related(const char *path)
{
	const char *base;

	if (!path)
		return false;

	if (xg_path_has_prefix(path, "/data/adb") ||
	    strstr(path, "/data/adb/") || strstr(path, "Magisk") ||
	    strstr(path, "magisk") || strstr(path, "KernelSU") ||
	    strstr(path, "kernelsu") || strstr(path, "SukiSU") ||
	    strstr(path, "sukisu"))
		return true;

	if (xg_path_has_component(path, "ksu") ||
	    xg_path_has_component(path, "ksud") ||
	    xg_path_has_component(path, "magiskd"))
		return true;

	base = xg_basename(path);
	return xg_name_is_root_manager_token(base);
}

static void xg_note_root_manager_path(const char *hook, const char *path)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	if (xg_path_is_post_fs_data_related(path)) {
		pr_warn_ratelimited(
		    XG_TAG ": marked post-fs-data sandbox via %s stage=%s "
			   "pid=%d uid=%u comm=%s path=%.*s\n",
		    hook, xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm, (int)path_len,
		    path ? path : "");
		xg_mark_current_postfs_sandbox("post-fs-data module script",
					       path);
		return;
	}

	if (xg_policy_is_enforced())
		return;

	if (!xg_path_is_root_manager_related(path))
		return;

	pr_info_ratelimited(XG_TAG ": deferred root-manager trigger via %s "
				   "stage=%s pid=%d uid=%u comm=%s path=%.*s\n",
			    hook, xg_stage_name(xg_current_stage()), current->pid,
			    __kuid_val(current_uid()), current->comm,
			    (int)path_len, path ? path : "");
}

static bool xg_path_has_tmp_marker(const char *path)
{
	return path &&
	       (strstr(path, "gztmp") || strstr(path, "/tmp/") ||
		strstr(path, "/data/local/tmp/") || strstr(path, "/cache/") ||
		strstr(path, "/code_cache/") || strstr(path, "/dev/shm/"));
}

static bool xg_path_has_gzexe_tmp_marker(const char *path)
{
	return path && strstr(path, "gztmp");
}

static bool xg_path_is_system_exec_path(const char *path)
{
	return xg_path_has_prefix(path, "/system/bin/") ||
	       xg_path_has_prefix(path, "/system/xbin/") ||
	       xg_path_has_prefix(path, "/vendor/bin/") ||
	       xg_path_has_prefix(path, "/product/bin/") ||
	       xg_path_has_prefix(path, "/odm/bin/") ||
	       xg_path_has_prefix(path, "/apex/");
}

static bool xg_path_is_trusted_system_shell(const char *path, const char *base)
{
	if (!path || !xg_basename_is_shell_like(base))
		return false;

	return xg_name_eq(path, "/system/bin/sh") ||
	       xg_name_eq(path, "/system/bin/mksh") ||
	       xg_name_eq(path, "/vendor/bin/sh") ||
	       xg_name_eq(path, "/product/bin/sh") ||
	       xg_name_eq(path, "/odm/bin/sh");
}

static bool xg_basename_is_randomish(const char *base)
{
	size_t i;
	size_t len;
	unsigned int alnum = 0;
	unsigned int digits = 0;

	if (!base)
		return false;

	len = strnlen(base, XG_EXEC_PATH_LOG_BYTES);
	if (len < 8 || len > 32)
		return false;

	for (i = 0; i < len; i++) {
		char c = base[i];

		if (c == '.')
			return false;

		if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') ||
		    (c >= 'A' && c <= 'Z')) {
			alnum++;
			if (c >= '0' && c <= '9')
				digits++;
			continue;
		}

		return false;
	}

	return alnum == len && digits * 3 >= len;
}

static char *xg_file_path(struct file *file, char *buf, size_t buflen)
{
	char *path;

	if (!file || !buf || !buflen)
		return NULL;

	path = d_path(&file->f_path, buf, buflen);
	return IS_ERR(path) ? NULL : path;
}

static u32 xg_cred_selinux_sid(const struct cred *cred)
{
	struct xg_selinux_task_security *tsec;

	if (!cred || !cred->security)
		return 0;

	tsec = (struct xg_selinux_task_security *)cred->security;
	return READ_ONCE(tsec->sid);
}

static bool xg_current_is_privileged_dynamic_subject(void)
{
	unsigned int uid = __kuid_val(current_uid());

	return uid == 0 || uid == 2000;
}

static bool xg_exec_guard_applies(void)
{
	if (!xg_stage_allows_exec_policy())
		return false;

	if (READ_ONCE(xg_watchdog_tripped))
		return true;

	return xg_exec_guard_all_uid ||
	       xg_current_is_privileged_dynamic_subject();
}

static void xg_record_exec_identity(struct xg_trusted_exec_identity *identity,
				    struct file *file)
{
	struct inode *inode;

	if (!identity || !file)
		return;

	inode = file_inode(file);
	if (!inode || !S_ISREG(inode->i_mode))
		return;

	identity->sb_dev = inode->i_sb ? inode->i_sb->s_dev : 0;
	identity->ino = inode->i_ino;
	identity->size = i_size_read(inode);
	identity->mode = inode->i_mode;
	identity->valid = true;
}

static bool
xg_exec_identity_matches(const struct xg_trusted_exec_identity *identity,
			 struct file *file)
{
	struct inode *inode;

	if (!identity || !identity->valid || !file)
		return false;

	inode = file_inode(file);
	if (!inode)
		return false;

	return identity->sb_dev == (inode->i_sb ? inode->i_sb->s_dev : 0) &&
	       identity->ino == inode->i_ino &&
	       identity->size == i_size_read(inode) &&
	       identity->mode == inode->i_mode;
}

static struct xg_trusted_exec_identity *xg_trusted_shell_slot(const char *path)
{
	unsigned int i;

	if (!path)
		return NULL;

	for (i = 0; i < ARRAY_SIZE(xg_trusted_shells); i++) {
		if (xg_name_eq(path, xg_trusted_shells[i].path))
			return &xg_trusted_shells[i];
	}

	return NULL;
}

static bool xg_file_is_trusted_system_shell(struct file *file, const char *path,
					    const char *base)
{
	struct xg_trusted_exec_identity *identity;
	unsigned long flags;
	bool trusted;

	if (!xg_path_is_trusted_system_shell(path, base))
		return false;

	identity = xg_trusted_shell_slot(path);
	if (!identity)
		return false;

	spin_lock_irqsave(&xg_trusted_shell_lock, flags);
	if (!identity->valid) {
		xg_record_exec_identity(identity, file);
		trusted = identity->valid;
		if (trusted)
			pr_info(XG_TAG ": learned trusted shell %s dev=%u:%u "
				       "ino=%lu size=%lld\n",
				identity->path, MAJOR(identity->sb_dev),
				MINOR(identity->sb_dev), identity->ino,
				(long long)identity->size);
	} else {
		trusted = xg_exec_identity_matches(identity, file);
	}
	spin_unlock_irqrestore(&xg_trusted_shell_lock, flags);

	return trusted;
}

static bool xg_fdpath_is_empty_exec(const char *path)
{
	const char *p;

	if (!path || strncmp(path, "/dev/fd/", strlen("/dev/fd/")))
		return false;

	p = path + strlen("/dev/fd/");
	if (*p < '0' || *p > '9')
		return false;

	while (*p >= '0' && *p <= '9')
		p++;

	return *p == '\0';
}

static bool xg_elf_has_packed_layout(const u8 *buf, size_t len,
				     const Elf64_Ehdr *ehdr)
{
	const Elf64_Phdr *phdr;
	unsigned int i;
	bool executable_load = false;
	bool inflated_writable_load = false;
	size_t phdr_size;
	size_t phdr_end;

	if (ehdr->e_phentsize != sizeof(*phdr) || !ehdr->e_phnum)
		return false;

	if (ehdr->e_phoff > len)
		return false;

	if (check_mul_overflow((size_t)ehdr->e_phnum, sizeof(*phdr),
			       &phdr_size))
		return false;

	if (check_add_overflow((size_t)ehdr->e_phoff, phdr_size, &phdr_end) ||
	    phdr_end > len)
		return false;

	phdr = (const Elf64_Phdr *)(buf + ehdr->e_phoff);
	for (i = 0; i < ehdr->e_phnum; i++) {
		if (phdr[i].p_type != PT_LOAD)
			continue;

		if (phdr[i].p_flags & PF_X)
			executable_load = true;

		if ((phdr[i].p_flags & PF_W) &&
		    phdr[i].p_memsz > phdr[i].p_filesz &&
		    phdr[i].p_memsz - phdr[i].p_filesz > SZ_1M &&
		    phdr[i].p_memsz / 2 > phdr[i].p_filesz)
			inflated_writable_load = true;
	}

	return executable_load && inflated_writable_load;
}

static void xg_classify_exec_buf(const char *path, const u8 *buf, size_t len,
				 struct xg_exec_features *features)
{
	const Elf64_Ehdr *ehdr;

	memset(features, 0, sizeof(*features));

	if (!buf || !len)
		return;

	if (len >= SELFMAG && !memcmp(buf, ELFMAG, SELFMAG)) {
		features->elf = true;
		if (len < sizeof(*ehdr))
			return;

		ehdr = (const Elf64_Ehdr *)buf;
		features->elf_aarch64 = ehdr->e_machine == EM_AARCH64;
		features->elf_no_sections =
		    !ehdr->e_shoff || !ehdr->e_shnum || !ehdr->e_shentsize;
		features->elf_packed_layout =
		    features->elf_no_sections &&
		    xg_elf_has_packed_layout(buf, len, ehdr);
		return;
	}

	features->gzexe = xg_block_gzexe_exec &&
			  xg_mem_has(buf, len, "#gzexe") &&
			  xg_mem_has(buf, len, "skip=");

	if (!features->gzexe && path &&
	    xg_name_has_suffix(xg_basename(path), ".sh") &&
	    xg_mem_has(buf, len, "gzip"))
		features->gzexe = xg_mem_has(buf, len, "skip=");
}

static void xg_log_blocked_exec(const char *hook, const char *reason,
				const char *path,
				const struct xg_exec_features *features)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_warn_ratelimited(
	    XG_TAG
	    ": blocked exec via %s reason=%s pid=%d uid=%u comm=%s path=%.*s "
	    "elf=%u aarch64=%u no_sections=%u packed=%u gzexe=%u fd_empty=%u "
	    "trusted_shell=%u\n",
	    hook, reason, current->pid, __kuid_val(current_uid()),
	    current->comm, (int)path_len, path ? path : "",
	    features ? features->elf : 0, features ? features->elf_aarch64 : 0,
	    features ? features->elf_no_sections : 0,
	    features ? features->elf_packed_layout : 0,
	    features ? features->gzexe : 0,
	    features ? features->fd_empty_path : 0,
	    features ? features->trusted_system_shell : 0);
}

static void xg_log_audited_exec(const char *hook, const char *reason,
				const char *path,
				const struct xg_exec_features *features)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_info_ratelimited(
	    XG_TAG
	    ": audited exec via %s reason=%s stage=%s pid=%d uid=%u comm=%s "
	    "path=%.*s elf=%u aarch64=%u no_sections=%u packed=%u gzexe=%u "
	    "fd_empty=%u trusted_shell=%u\n",
	    hook, reason, xg_stage_name(xg_current_stage()), current->pid,
	    __kuid_val(current_uid()), current->comm, (int)path_len,
	    path ? path : "", features ? features->elf : 0,
	    features ? features->elf_aarch64 : 0,
	    features ? features->elf_no_sections : 0,
	    features ? features->elf_packed_layout : 0,
	    features ? features->gzexe : 0,
	    features ? features->fd_empty_path : 0,
	    features ? features->trusted_system_shell : 0);
}

static void xg_log_blocked_shell_script(const char *hook, const char *reason,
					const char *script)
{
	unsigned int script_len =
	    script ? strnlen(script, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_warn_ratelimited(
	    XG_TAG ": blocked shell script via %s reason=%s pid=%d "
		   "uid=%u comm=%s script=%.*s\n",
	    hook, reason, current->pid, __kuid_val(current_uid()),
	    current->comm, (int)script_len, script ? script : "");
}

static void xg_log_audited_shell_script(const char *hook, const char *reason,
					const char *script)
{
	unsigned int script_len =
	    script ? strnlen(script, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_info_ratelimited(
	    XG_TAG ": audited shell script via %s reason=%s stage=%s pid=%d "
		   "uid=%u comm=%s script=%.*s\n",
	    hook, reason, xg_stage_name(xg_current_stage()), current->pid,
	    __kuid_val(current_uid()), current->comm, (int)script_len,
	    script ? script : "");
}

static unsigned int xg_gzexe_sample_seen_bits(const u8 *sample,
					      size_t sample_len)
{
	unsigned int seen = 0;

	if (!sample || !sample_len)
		return 0;

	if (xg_mem_has(sample, sample_len, "#gzexe") ||
	    xg_mem_has(sample, sample_len, "gzexe"))
		seen |= XG_GZEXE_SEEN_MAGIC;

	if (xg_mem_has(sample, sample_len, "skip=") ||
	    xg_mem_has(sample, sample_len, "skip ="))
		seen |= XG_GZEXE_SEEN_SKIP;

	if (xg_mem_has(sample, sample_len, "gzip") ||
	    xg_mem_has(sample, sample_len, "gunzip") ||
	    xg_mem_has(sample, sample_len, "zcat"))
		seen |= XG_GZEXE_SEEN_DECOMPRESS;

	if (xg_mem_has(sample, sample_len, "gztmp") ||
	    xg_mem_has(sample, sample_len, "$tmp") ||
	    xg_mem_has(sample, sample_len, "${tmp"))
		seen |= XG_GZEXE_SEEN_TMP;

	return seen;
}

static bool xg_gzexe_seen_blocks(unsigned int seen)
{
	if ((seen & XG_GZEXE_SEEN_MAGIC) && (seen & XG_GZEXE_SEEN_SKIP))
		return true;

	return (seen & XG_GZEXE_SEEN_SKIP) &&
	       (seen & XG_GZEXE_SEEN_DECOMPRESS) && (seen & XG_GZEXE_SEEN_TMP);
}

static const char *xg_shell_script_sample_block_reason(const char *path,
						       const u8 *sample,
						       size_t sample_len)
{
	struct xg_exec_features features;
	unsigned int seen;

	if (!sample || !sample_len)
		return NULL;

	xg_classify_exec_buf(path, sample, sample_len, &features);
	if (features.gzexe)
		return "gzexe self-extracting shell read";

	seen = xg_gzexe_sample_seen_bits(sample, sample_len);
	if (xg_gzexe_seen_blocks(seen))
		return "gzexe-like shell read";

	return NULL;
}

static void xg_mark_tagged_task_window(struct xg_tagged_task *tasks,
				       unsigned int count, spinlock_t *lock,
				       pid_t tgid, u32 sid,
				       unsigned int max_age_ms)
{
	struct xg_tagged_task *slot = NULL;
	unsigned long flags;
	unsigned long now = jiffies;
	unsigned long max_age = msecs_to_jiffies(max_age_ms);
	unsigned int i;

	if (tgid <= 0)
		return;

	spin_lock_irqsave(lock, flags);
	for (i = 0; i < count; i++) {
		if (tasks[i].tgid == tgid) {
			slot = &tasks[i];
			break;
		}

		if (!slot && (!tasks[i].tgid ||
			      time_after(now, tasks[i].last_seen + max_age)))
			slot = &tasks[i];
	}

	if (!slot)
		slot = &tasks[0];

	slot->tgid = tgid;
	slot->sid = sid;
	slot->last_seen = now;
	spin_unlock_irqrestore(lock, flags);
}

static void xg_mark_tagged_task(struct xg_tagged_task *tasks,
				unsigned int count, spinlock_t *lock,
				pid_t tgid, u32 sid)
{
	xg_mark_tagged_task_window(tasks, count, lock, tgid, sid,
				   XG_GZEXE_CHAIN_WINDOW_MS);
}

static bool xg_tagged_tgid_window(struct xg_tagged_task *tasks,
				  unsigned int count, spinlock_t *lock,
				  pid_t tgid, u32 sid, bool expire,
				  unsigned int max_age_ms)
{
	unsigned long flags;
	unsigned long now = jiffies;
	unsigned long max_age = msecs_to_jiffies(max_age_ms);
	unsigned int i;
	bool tagged = false;

	if (tgid <= 0)
		return false;

	spin_lock_irqsave(lock, flags);
	for (i = 0; i < count; i++) {
		if (tasks[i].tgid != tgid)
			continue;
		if (expire && time_after(now, tasks[i].last_seen + max_age))
			continue;
		if (!tasks[i].sid || !sid || tasks[i].sid == sid) {
			tagged = true;
			break;
		}
	}
	spin_unlock_irqrestore(lock, flags);

	return tagged;
}

static bool xg_tagged_tgid(struct xg_tagged_task *tasks, unsigned int count,
			   spinlock_t *lock, pid_t tgid, u32 sid, bool expire)
{
	return xg_tagged_tgid_window(tasks, count, lock, tgid, sid, expire,
				     XG_GZEXE_CHAIN_WINDOW_MS);
}

static void xg_mark_postfs_sandbox_tgid(pid_t tgid, u32 sid,
					const char *reason, const char *path)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	if (tgid <= 0)
		return;

	xg_mark_tagged_task_window(xg_postfs_sandbox_tasks,
				   ARRAY_SIZE(xg_postfs_sandbox_tasks),
				   &xg_postfs_sandbox_lock, tgid, sid,
				   XG_POSTFS_SANDBOX_WINDOW_MS);

	pr_warn_ratelimited(
	    XG_TAG ": post-fs-data sandbox task reason=%s stage=%s pid=%d "
		   "tgid=%d uid=%u comm=%s target_tgid=%d sid=%u path=%.*s\n",
	    reason ? reason : "post-fs-data sandbox", xg_stage_name(xg_current_stage()),
	    current->pid, current->tgid, __kuid_val(current_uid()),
	    current->comm, tgid, sid, (int)path_len, path ? path : "");
}

static void xg_mark_current_postfs_sandbox(const char *reason,
					   const char *path)
{
	const struct cred *cred = current_cred();
	u32 sid = xg_cred_selinux_sid(cred);

	xg_mark_postfs_sandbox_tgid(current->tgid, sid, reason, path);
}

static bool xg_current_postfs_sandboxed(const char **reason)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	if (xg_tagged_tgid_window(
		xg_postfs_sandbox_tasks,
		ARRAY_SIZE(xg_postfs_sandbox_tasks),
		&xg_postfs_sandbox_lock, current->tgid, sid, true,
		XG_POSTFS_SANDBOX_WINDOW_MS)) {
		if (reason)
			*reason = "post-fs-data sandbox";
		return true;
	}

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (xg_tagged_tgid_window(xg_postfs_sandbox_tasks,
				  ARRAY_SIZE(xg_postfs_sandbox_tasks),
				  &xg_postfs_sandbox_lock, parent_tgid, 0,
				  true, XG_POSTFS_SANDBOX_WINDOW_MS)) {
		xg_mark_postfs_sandbox_tgid(current->tgid, sid,
					    "child of post-fs-data sandbox",
					    NULL);
		if (reason)
			*reason = "child of post-fs-data sandbox";
		return true;
	}

	return false;
}

static bool xg_task_comm_is_shell_like(struct task_struct *task)
{
	if (!task)
		return false;

	return !strncmp(task->comm, "sh", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "mksh", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "ash", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "bash", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "dash", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "zsh", TASK_COMM_LEN) ||
	       !strncmp(task->comm, "fish", 4);
}

static bool xg_mark_shell_polluted(struct task_struct *task, const char *reason)
{
	const struct cred *cred;
	u32 sid = 0;

	if (!xg_strict_shell_exec_guard || !xg_task_comm_is_shell_like(task))
		return false;

	cred = get_task_cred(task);
	if (cred) {
		sid = xg_cred_selinux_sid(cred);
		put_cred(cred);
	}

	xg_mark_tagged_task(xg_shell_polluted_tasks,
			    ARRAY_SIZE(xg_shell_polluted_tasks),
			    &xg_shell_polluted_lock, task->tgid, sid);

	pr_warn_ratelimited(
	    XG_TAG ": marked shell polluted via %s tracer_pid=%d tracer_uid=%u "
		   "tracer_comm=%s target_pid=%d target_tgid=%d target_comm=%s "
		   "sid=%u\n",
	    reason, current->pid, __kuid_val(current_uid()), current->comm,
	    task->pid, task->tgid, task->comm, sid);
	return true;
}

static bool xg_current_shell_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	if (!xg_strict_shell_exec_guard)
		return false;

	if (xg_tagged_tgid(
		xg_shell_polluted_tasks, ARRAY_SIZE(xg_shell_polluted_tasks),
		&xg_shell_polluted_lock, current->tgid, sid, false)) {
		if (reason)
			*reason = "polluted shell raw block-device write";
		return true;
	}

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (xg_tagged_tgid(xg_shell_polluted_tasks,
			   ARRAY_SIZE(xg_shell_polluted_tasks),
			   &xg_shell_polluted_lock, parent_tgid, 0, false)) {
		if (reason)
			*reason =
			    "child of polluted shell raw block-device write";
		return true;
	}

	return false;
}

static void xg_mark_gzexe_tgid(pid_t tgid, u32 sid, const char *reason,
			       const char *path)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	xg_mark_tagged_task(xg_gzexe_polluted_tasks,
			    ARRAY_SIZE(xg_gzexe_polluted_tasks),
			    &xg_gzexe_polluted_lock, tgid, sid);

	pr_warn_ratelimited(
	    XG_TAG ": marked gzexe execution chain via %s reader_pid=%d "
		   "reader_tgid=%d "
		   "uid=%u reader_comm=%s target_tgid=%d sid=%u path=%.*s\n",
	    reason, current->pid, current->tgid, __kuid_val(current_uid()),
	    current->comm, tgid, sid, (int)path_len, path ? path : "");
}

static void xg_mark_current_gzexe_chain(const char *reason, const char *path)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	xg_mark_gzexe_tgid(current->tgid, sid, reason, path);

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (parent_tgid && parent_tgid != current->tgid)
		xg_mark_gzexe_tgid(parent_tgid, sid, reason, path);
}

static bool xg_current_gzexe_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	struct task_struct *parent;
	pid_t parent_tgid = 0;
	u32 sid = xg_cred_selinux_sid(cred);

	if (!xg_block_gzexe_exec)
		return false;

	if (xg_tagged_tgid(xg_gzexe_polluted_tasks,
			   ARRAY_SIZE(xg_gzexe_polluted_tasks),
			   &xg_gzexe_polluted_lock, current->tgid, sid, true)) {
		if (reason)
			*reason = "gzexe-read polluted execution chain";
		return true;
	}

	rcu_read_lock();
	parent = rcu_dereference(current->real_parent);
	if (parent)
		parent_tgid = parent->tgid;
	rcu_read_unlock();

	if (xg_tagged_tgid(xg_gzexe_polluted_tasks,
			   ARRAY_SIZE(xg_gzexe_polluted_tasks),
			   &xg_gzexe_polluted_lock, parent_tgid, 0, true)) {
		if (reason)
			*reason =
			    "child of gzexe-read polluted execution chain";
		return true;
	}

	return false;
}

static bool xg_current_dynamic_code_polluted(const char **reason)
{
	const struct cred *cred = current_cred();
	u32 sid = xg_cred_selinux_sid(cred);

	if (xg_tagged_tgid(xg_dynamic_tasks, ARRAY_SIZE(xg_dynamic_tasks),
			   &xg_dynamic_lock, current->tgid, sid, false)) {
		if (reason)
			*reason =
			    "dynamic-code polluted raw block-device write";
		return true;
	}

	return false;
}

static void xg_mark_current_dynamic_code(const char *reason, unsigned long addr,
					 unsigned long len)
{
	const struct cred *cred = current_cred();
	unsigned int uid = __kuid_val(cred->uid);
	u32 sid = xg_cred_selinux_sid(cred);

	xg_mark_tagged_task(xg_dynamic_tasks, ARRAY_SIZE(xg_dynamic_tasks),
			    &xg_dynamic_lock, current->tgid, sid);

	pr_warn_ratelimited(
	    XG_TAG ": marked suspicious dynamic code via %s stage=%s pid=%d "
		   "tgid=%d uid=%u comm=%s sid=%u addr=0x%lx len=%lu\n",
	    reason, xg_stage_name(xg_current_stage()), current->pid,
	    current->tgid, uid, current->comm, sid, addr, len);
}

static struct partition_meta_info *xg_bdev_meta_info(struct block_device *bdev)
{
	if (!bdev)
		return NULL;

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 11, 0)
	if (!bdev->bd_part)
		return NULL;

	return bdev->bd_part->info;
#else
	return bdev->bd_meta_info;
#endif
}

static const u8 *xg_bdev_part_name(struct block_device *bdev, unsigned int *len)
{
	struct partition_meta_info *info;
	const u8 *name;
	unsigned int i;

	if (len)
		*len = 0;

	info = xg_bdev_meta_info(bdev);
	if (!info)
		return NULL;

	name = info->volname;
	if (!name[0])
		return NULL;

	for (i = 0; i < PARTITION_META_INFO_VOLNAMELTH && name[i]; i++) {
	}

	if (!i)
		return NULL;

	if (len)
		*len = i;
	return name;
}

static bool xg_part_name_eq(const u8 *name, unsigned int len, const char *base)
{
	size_t base_len = strlen(base);

	if (len == base_len && !memcmp(name, base, base_len))
		return true;

	if (len == base_len + 2 && !memcmp(name, base, base_len) &&
	    name[base_len] == '_' &&
	    (name[base_len + 1] == 'a' || name[base_len + 1] == 'b'))
		return true;

	return false;
}

static bool xg_part_in_list(const u8 *name, unsigned int len,
			    const char *const *list, unsigned int count)
{
	unsigned int i;

	if (!name || !len)
		return false;

	for (i = 0; i < count; i++) {
		if (xg_part_name_eq(name, len, list[i]))
			return true;
	}

	return false;
}

static enum xg_part_class xg_classify_part_name(const u8 *name, unsigned int len)
{
	if (!name)
		return XG_PART_GENERIC;

	if (xg_part_in_list(name, len, xg_firmware_partitions,
			    ARRAY_SIZE(xg_firmware_partitions)))
		return XG_PART_FIRMWARE;

	if (xg_part_in_list(name, len, xg_radio_nv_partitions,
			    ARRAY_SIZE(xg_radio_nv_partitions)))
		return XG_PART_RADIO_NV;

	return XG_PART_GENERIC;
}

static enum xg_part_class xg_classify_bdev(struct block_device *bdev)
{
	const u8 *name;
	unsigned int len;

	name = xg_bdev_part_name(bdev, &len);
	return xg_classify_part_name(name, len);
}

static struct block_device *xg_file_bdev(struct file *file)
{
	struct inode *inode;

	if (!file)
		return NULL;

	inode = file_inode(file);
	if (!inode || !S_ISBLK(inode->i_mode))
		return NULL;

	return file->private_data;
}

static bool xg_bdev_is_zram(struct block_device *bdev)
{
	const char *name;

	if (!bdev || !bdev->bd_disk)
		return false;

	name = bdev->bd_disk->disk_name;
	return name && !strncmp(name, "zram", 4);
}

static struct xg_radio_nv_profile *xg_radio_nv_profile_slot(dev_t dev)
{
	struct xg_radio_nv_profile *free_slot = NULL;
	unsigned int i;

	if (!dev)
		return NULL;

	for (i = 0; i < ARRAY_SIZE(xg_radio_nv_profiles); i++) {
		if (xg_radio_nv_profiles[i].valid &&
		    xg_radio_nv_profiles[i].dev == dev)
			return &xg_radio_nv_profiles[i];

		if (!free_slot && !xg_radio_nv_profiles[i].valid)
			free_slot = &xg_radio_nv_profiles[i];
	}

	return free_slot ? free_slot : &xg_radio_nv_profiles[0];
}

static dev_t xg_bdev_dev(struct file *file, struct block_device *bdev)
{
	struct inode *inode;

	if (bdev)
		return bdev->bd_dev;

	if (!file)
		return 0;

	inode = file_inode(file);
	return inode ? inode->i_rdev : 0;
}

static void xg_radio_nv_learn_write(dev_t dev, loff_t pos, u64 count,
				    const char *hook)
{
	struct xg_radio_nv_profile *profile;
	unsigned long flags;
	u64 start;
	u64 end;

	if (!dev || pos < 0 || !count)
		return;

	start = (u64)pos;
	if (check_add_overflow(start, count, &end))
		end = U64_MAX;

	spin_lock_irqsave(&xg_radio_nv_profile_lock, flags);
	profile = xg_radio_nv_profile_slot(dev);
	if (profile) {
		if (!profile->valid || profile->dev != dev) {
			memset(profile, 0, sizeof(*profile));
			profile->dev = dev;
			profile->min_pos = start;
			profile->max_end = end;
			profile->max_count = count;
			profile->valid = true;
		} else {
			profile->min_pos = min(profile->min_pos, start);
			profile->max_end = max(profile->max_end, end);
			profile->max_count = max(profile->max_count, count);
		}
		profile->writes++;
	}
	spin_unlock_irqrestore(&xg_radio_nv_profile_lock, flags);

	pr_info_ratelimited(
	    XG_TAG ": learned radio NV write behavior via %s stage=%s "
		   "pid=%d uid=%u comm=%s dev=%u:%u pos=%llu count=%llu\n",
	    hook, xg_stage_name(xg_current_stage()), current->pid,
	    __kuid_val(current_uid()), current->comm, MAJOR(dev), MINOR(dev),
	    start, count);
}

static bool xg_radio_nv_profile_allows_write(dev_t dev, loff_t pos, u64 count,
					     const char **reason)
{
	struct xg_radio_nv_profile snapshot = {};
	struct xg_radio_nv_profile *profile;
	unsigned long flags;
	u64 start;
	u64 end;
	bool found = false;

	if (!dev || pos < 0 || !count) {
		if (reason)
			*reason = "invalid radio NV write geometry";
		return false;
	}

	start = (u64)pos;
	if (check_add_overflow(start, count, &end))
		end = U64_MAX;

	spin_lock_irqsave(&xg_radio_nv_profile_lock, flags);
	profile = xg_radio_nv_profile_slot(dev);
	if (profile && profile->valid && profile->dev == dev) {
		snapshot = *profile;
		found = true;
	}
	spin_unlock_irqrestore(&xg_radio_nv_profile_lock, flags);

	if (!found || snapshot.writes < XG_RADIO_NV_PROFILE_MIN_COUNT) {
		if (count <= XG_SINGLE_DEV_EARLY_LIMIT &&
		    start >= XG_EARLY_WINDOW_BYTES) {
			if (reason)
				*reason = "radio NV conservative unlearned write";
			return true;
		}
		if (reason)
			*reason = "radio NV write before stable profile";
		return false;
	}

	if (count > snapshot.max_count + XG_RADIO_NV_PROFILE_TOLERANCE_BYTES) {
		if (reason)
			*reason = "radio NV write larger than learned behavior";
		return false;
	}

	if (end < snapshot.min_pos ||
	    start > snapshot.max_end + XG_RADIO_NV_PROFILE_TOLERANCE_BYTES) {
		if (reason)
			*reason = "radio NV write outside learned range";
		return false;
	}

	if (reason)
		*reason = "radio NV write matched learned behavior";
	return true;
}

static bool xg_bdev_range_overlaps(struct block_device *part, sector_t start,
				   sector_t end)
{
	sector_t part_start;
	sector_t part_end;

	if (!part || !bdev_nr_sectors(part))
		return false;

	part_start = part->bd_start_sect;
	part_end = part_start + bdev_nr_sectors(part);
	return start < part_end && end > part_start;
}

static bool xg_bdev_has_protected_part(struct block_device *bdev,
				       enum xg_part_class *classp)
{
	struct block_device *part;
	unsigned long idx;
	bool found = false;

	if (!bdev || !bdev->bd_disk)
		return false;

	rcu_read_lock();
	xa_for_each_start(&bdev->bd_disk->part_tbl, idx, part, 1) {
		enum xg_part_class part_class;

		part_class = xg_classify_bdev(part);
		if (part_class == XG_PART_GENERIC)
			continue;

		if (classp)
			*classp = part_class;
		found = true;
		break;
	}
	rcu_read_unlock();

	return found;
}

static enum xg_part_class xg_classify_bdev_range(struct block_device *bdev,
						 loff_t pos, u64 count)
{
	struct block_device *part;
	enum xg_part_class class;
	unsigned long idx;
	sector_t start;
	sector_t end;
	u64 end_byte;
	u64 rounded_end;

	class = xg_classify_bdev(bdev);
	if (class != XG_PART_GENERIC || !bdev || !bdev->bd_disk || pos < 0 ||
	    !count)
		return class;

	if (check_add_overflow((u64)pos, count, &end_byte) ||
	    check_add_overflow(end_byte, (u64)SECTOR_SIZE - 1, &rounded_end))
		end = (sector_t)-1;
	else
		end = (sector_t)(rounded_end >> SECTOR_SHIFT);

	start = (sector_t)((u64)pos >> SECTOR_SHIFT);
	if (bdev_is_partition(bdev)) {
		start += bdev->bd_start_sect;
		if (end != (sector_t)-1)
			end += bdev->bd_start_sect;
	}

	rcu_read_lock();
	xa_for_each_start(&bdev->bd_disk->part_tbl, idx, part, 1) {
		if (!xg_bdev_range_overlaps(part, start, end))
			continue;

		class = xg_classify_bdev(part);
		if (class != XG_PART_GENERIC)
			break;
	}
	rcu_read_unlock();

	return class;
}

static void xg_log_blocked_access(const char *hook, const char *op,
				  struct file *file, struct block_device *bdev,
				  u64 pos, u64 count, unsigned int cmd)
{
	const u8 *part;
	unsigned int part_len = 0;
	dev_t dev;

	if (!bdev)
		bdev = xg_file_bdev(file);

	dev = xg_bdev_dev(file, bdev);
	part = xg_bdev_part_name(bdev, &part_len);

	if (part_len) {
		pr_warn_ratelimited(
		    XG_TAG ": blocked %s via %s pid=%d uid=%u comm=%s "
			   "dev=%u:%u part=%.*s pos=%llu count=%llu "
			   "cmd=0x%x\n",
		    op, hook, current->pid, __kuid_val(current_uid()),
		    current->comm, MAJOR(dev), MINOR(dev), part_len, part,
		    pos, count, cmd);
	} else {
		pr_warn_ratelimited(XG_TAG ": blocked %s via %s pid=%d uid=%u "
					   "comm=%s dev=%u:%u pos=%llu "
					   "count=%llu cmd=0x%x\n",
				    op, hook, current->pid,
				    __kuid_val(current_uid()), current->comm,
				    MAJOR(dev), MINOR(dev), pos, count, cmd);
	}
}

static void xg_log_audited_access(const char *hook, const char *op,
				  struct file *file, struct block_device *bdev,
				  u64 pos, u64 count, unsigned int cmd)
{
	const u8 *part;
	unsigned int part_len = 0;
	dev_t dev;

	if (!bdev)
		bdev = xg_file_bdev(file);

	dev = xg_bdev_dev(file, bdev);
	part = xg_bdev_part_name(bdev, &part_len);

	if (part_len) {
		pr_info_ratelimited(
		    XG_TAG ": audited %s via %s stage=%s pid=%d uid=%u "
			   "comm=%s dev=%u:%u part=%.*s pos=%llu count=%llu "
			   "cmd=0x%x\n",
		    op, hook, xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm, MAJOR(dev),
		    MINOR(dev), part_len, part, pos, count, cmd);
	} else {
		pr_info_ratelimited(
		    XG_TAG ": audited %s via %s stage=%s pid=%d uid=%u "
			   "comm=%s dev=%u:%u pos=%llu count=%llu cmd=0x%x\n",
		    op, hook, xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm, MAJOR(dev),
		    MINOR(dev), pos, count, cmd);
	}
}

static bool xg_is_destructive_blk_ioctl(unsigned int cmd)
{
	switch (cmd) {
	case BLKROSET:
	case BLKRRPART:
	case BLKPG:
	case BLKRASET:
	case BLKFRASET:
	case BLKSECTSET:
	case BLKBSZSET:
	case BLKDISCARD:
	case BLKSECDISCARD:
	case BLKZEROOUT:
#ifdef BLKRESETZONE
	case BLKRESETZONE:
#endif
#ifdef BLKOPENZONE
	case BLKOPENZONE:
#endif
#ifdef BLKCLOSEZONE
	case BLKCLOSEZONE:
#endif
#ifdef BLKFINISHZONE
	case BLKFINISHZONE:
#endif
		return true;
	default:
		return false;
	}
}

static bool xg_is_erasing_blk_ioctl(unsigned int cmd)
{
	switch (cmd) {
	case BLKDISCARD:
	case BLKSECDISCARD:
	case BLKZEROOUT:
#ifdef BLKRESETZONE
	case BLKRESETZONE:
#endif
		return true;
	default:
		return false;
	}
}

static bool xg_audit_raw_block_if_not_enforced(const char *hook,
					       struct file *file,
					       bool destructive_ioctl,
					       unsigned int cmd)
{
	struct block_device *bdev = xg_file_bdev(file);

	if (!bdev || xg_policy_subject_can_block())
		return false;

	xg_log_audited_access(hook,
			      destructive_ioctl ?
				  "raw block-device destructive ioctl" :
				  "raw block-device write-capable access",
			      file, bdev, 0, 0, cmd);
	return true;
}

static bool xg_raw_block_should_block(struct file *file, bool destructive_ioctl,
				      unsigned int cmd, const char **reason)
{
	struct block_device *bdev = xg_file_bdev(file);
	enum xg_part_class part_class;
	bool postfs_sandboxed;

	if (!bdev)
		return false;

	if (!xg_policy_subject_can_block())
		return false;

	postfs_sandboxed = xg_current_postfs_sandboxed(NULL);

	if (xg_current_dynamic_code_polluted(reason))
		return true;

	if (xg_current_gzexe_polluted(reason))
		return true;

	if (xg_current_shell_polluted(reason))
		return true;

	part_class = xg_classify_bdev(bdev);
	if (part_class == XG_PART_GENERIC && !bdev_is_partition(bdev) &&
	    xg_bdev_has_protected_part(bdev, &part_class)) {
		*reason = destructive_ioctl ?
			      "protected partition ioctl via whole disk" :
			      "protected partition raw write via whole disk";
		return true;
	}

	if (part_class == XG_PART_FIRMWARE) {
		*reason = destructive_ioctl
			      ? "protected firmware partition ioctl"
			      : "protected firmware partition raw write";
		return true;
	}

	if (part_class == XG_PART_RADIO_NV && destructive_ioctl &&
	    xg_is_erasing_blk_ioctl(cmd)) {
		*reason = "protected radio NV partition erasing ioctl";
		return true;
	}
	if (part_class == XG_PART_RADIO_NV && destructive_ioctl)
		return false;

	if (destructive_ioctl && postfs_sandboxed &&
	    !xg_bdev_is_zram(bdev)) {
		*reason = "post-fs-data sandbox destructive raw block ioctl";
		return true;
	}

	return false;
}

static bool xg_sample_is_zero(const u8 *sample, size_t len)
{
	size_t i;

	for (i = 0; i < len; i++) {
		if (sample[i])
			return false;
	}

	return len != 0;
}

static bool xg_user_write_sample_is_zero(const char __user *buf, size_t count)
{
	u8 sample[XG_WRITE_SAMPLE_BYTES];
	size_t len;

	if (!buf || !count)
		return false;

	len = min_t(size_t, count, sizeof(sample));
	if (copy_from_user(sample, buf, len))
		return false;

	return xg_sample_is_zero(sample, len);
}

static bool xg_iter_write_sample_is_zero(struct iov_iter *iter, size_t count)
{
	struct iov_iter iter_copy;
	u8 sample[XG_WRITE_SAMPLE_BYTES];
	size_t copied;
	size_t len;

	if (!iter || !count)
		return false;

	len = min_t(size_t, count, sizeof(sample));
	iter_copy = *iter;
	copied = copy_from_iter(sample, len, &iter_copy);
	if (copied != len)
		return false;

	return xg_sample_is_zero(sample, len);
}

static bool xg_write_sample_is_zero(const char __user *buf,
				    struct iov_iter *iter, size_t count)
{
	if (buf)
		return xg_user_write_sample_is_zero(buf, count);
	if (iter)
		return xg_iter_write_sample_is_zero(iter, count);
	return false;
}

static bool xg_user_write_sample_starts_with_elf(const char __user *buf,
						 size_t count)
{
	u8 sample[SELFMAG];

	if (!buf || count < sizeof(sample))
		return false;

	if (copy_from_user(sample, buf, sizeof(sample)))
		return false;

	return !memcmp(sample, ELFMAG, SELFMAG);
}

static bool xg_iter_write_sample_starts_with_elf(struct iov_iter *iter,
						 size_t count)
{
	struct iov_iter iter_copy;
	u8 sample[SELFMAG];
	size_t copied;

	if (!iter || count < sizeof(sample))
		return false;

	iter_copy = *iter;
	copied = copy_from_iter(sample, sizeof(sample), &iter_copy);
	if (copied != sizeof(sample))
		return false;

	return !memcmp(sample, ELFMAG, SELFMAG);
}

static bool xg_write_sample_starts_with_elf(const char __user *buf,
					    struct iov_iter *iter,
					    size_t count)
{
	if (buf)
		return xg_user_write_sample_starts_with_elf(buf, count);
	if (iter)
		return xg_iter_write_sample_starts_with_elf(iter, count);
	return false;
}

static loff_t xg_read_write_pos(struct file *file, loff_t *ppos)
{
	if (ppos)
		return READ_ONCE(*ppos);
	if (file)
		return READ_ONCE(file->f_pos);
	return 0;
}

static u64 xg_early_write_bytes(loff_t pos, u64 count)
{
	u64 early_end = XG_EARLY_WINDOW_BYTES;
	u64 start;

	if (!count || !early_end)
		return 0;

	if (pos < 0)
		return count;

	start = (u64)pos;
	if (start >= early_end)
		return 0;

	return min_t(u64, count, early_end - start);
}

static void xg_flow_reset(struct xg_write_flow *flow, pid_t tgid,
			  unsigned long now)
{
	memset(flow, 0, sizeof(*flow));
	flow->tgid = tgid;
	flow->last_seen = now;
}

static bool xg_flow_should_block(dev_t dev, loff_t pos, u64 count,
				 const char **reason)
{
	struct xg_write_flow *flow = NULL;
	struct xg_write_flow *stale = NULL;
	unsigned long flags;
	unsigned long now = jiffies;
	unsigned long window = msecs_to_jiffies(XG_FLOW_WINDOW_MS);
	u64 early;
	pid_t tgid = current->tgid;
	int dev_index = -1;
	unsigned int i;
	bool block = false;

	early = xg_early_write_bytes(pos, count);
	if (!early)
		return false;

	spin_lock_irqsave(&xg_flow_lock, flags);

	for (i = 0; i < ARRAY_SIZE(xg_flows); i++) {
		if (xg_flows[i].tgid == tgid &&
		    !time_after(now, xg_flows[i].last_seen + window)) {
			flow = &xg_flows[i];
			break;
		}

		if (!stale &&
		    (!xg_flows[i].tgid ||
		     time_after(now, xg_flows[i].last_seen + window)))
			stale = &xg_flows[i];
	}

	if (!flow) {
		flow = stale ? stale : &xg_flows[0];
		xg_flow_reset(flow, tgid, now);
	}

	flow->last_seen = now;

	for (i = 0; i < flow->dev_count; i++) {
		if (flow->devs[i] == dev) {
			dev_index = i;
			break;
		}
	}

	if (dev_index < 0) {
		if (!XG_MULTI_DEV_EARLY_LIMIT ||
		    (flow->dev_count &&
		     flow->dev_count + 1 >= XG_MULTI_DEV_EARLY_LIMIT)) {
			block = true;
			if (reason)
				*reason =
				    "early writes across multiple raw block devices";
			goto out;
		}

		if (flow->dev_count >= ARRAY_SIZE(flow->devs)) {
			block = true;
			if (reason)
				*reason =
				    "early writes across too many raw block devices";
			goto out;
		}

		dev_index = flow->dev_count;
		flow->devs[dev_index] = dev;
		flow->early_bytes[dev_index] = 0;
		flow->dev_count++;
	}

	if (!XG_SINGLE_DEV_EARLY_LIMIT ||
	    flow->early_bytes[dev_index] + early >
		XG_SINGLE_DEV_EARLY_LIMIT) {
		block = true;
		if (reason)
			*reason = "excessive early writes to raw block device";
		goto out;
	}

	flow->early_bytes[dev_index] += early;

out:
	spin_unlock_irqrestore(&xg_flow_lock, flags);
	return block;
}

static bool xg_gzexe_temp_payload_write_should_block(
    struct file *file, loff_t pos, size_t count, const char *hook,
    const char __user *buf, struct iov_iter *iter)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path;
	struct inode *inode;
	const char *reason = NULL;
	unsigned int path_len;
	bool gzexe_artifact;
	bool polluted;
	bool script_actor;
	bool suspicious_actor;
	bool temporary_elf;

	if (!xg_stage_allows_exec_policy() || !xg_block_gzexe_exec || !count ||
	    !file)
		return false;

	inode = file_inode(file);
	if (!inode || !S_ISREG(inode->i_mode))
		return false;

	path = xg_file_path(file, path_buf, sizeof(path_buf));
	if (!path)
		return false;

	gzexe_artifact = xg_path_has_gzexe_tmp_marker(path);
	polluted = xg_current_gzexe_polluted(&reason);
	script_actor = xg_current_is_script_reader_like();
	suspicious_actor =
	    script_actor || xg_current_is_privileged_dynamic_subject();
	temporary_elf =
	    pos <= 0 &&
	    (xg_path_has_tmp_marker(path) ||
	     xg_basename_is_randomish(xg_basename(path))) &&
	    xg_write_sample_starts_with_elf(buf, iter, count);

	if (!(gzexe_artifact && (polluted || suspicious_actor)) &&
	    !((polluted || script_actor) && temporary_elf))
		return false;

	if (!reason)
		reason = gzexe_artifact ? "gzexe temp extraction artifact write" :
					  "temporary ELF payload write";

	xg_mark_current_gzexe_chain(reason, path);
	path_len = strnlen(path, XG_EXEC_PATH_LOG_BYTES);
	if (!xg_policy_can_block(reason)) {
		pr_info_ratelimited(
		    XG_TAG ": audited gzexe temp payload write via %s reason=%s "
			   "stage=%s pid=%d uid=%u comm=%s path=%.*s "
			   "count=%llu\n",
		    hook, reason, xg_stage_name(xg_current_stage()),
		    current->pid, __kuid_val(current_uid()), current->comm,
		    (int)path_len, path, (unsigned long long)count);
		return false;
	}

	pr_warn_ratelimited(
	    XG_TAG ": blocked gzexe temp payload write via %s reason=%s "
		   "pid=%d uid=%u comm=%s path=%.*s count=%llu\n",
	    hook, reason, current->pid, __kuid_val(current_uid()),
	    current->comm, (int)path_len, path, (unsigned long long)count);
	return true;
}

static bool xg_raw_write_should_block(struct file *file, loff_t pos,
				      size_t count, const char *hook,
				      const char __user *buf,
				      struct iov_iter *iter)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path = NULL;
	struct block_device *bdev = xg_file_bdev(file);
	enum xg_part_class part_class;
	const char *reason = NULL;
	const char *learn_reason = NULL;
	dev_t dev;
	bool postfs_sandboxed;

	if (file)
		path = xg_file_path(file, path_buf, sizeof(path_buf));
	xg_note_root_manager_path(hook, path);

	if (!bdev || !count)
		return false;

	dev = xg_bdev_dev(file, bdev);
	part_class = xg_classify_bdev_range(bdev, pos, count);
	if (!xg_policy_subject_can_block()) {
		if (part_class == XG_PART_RADIO_NV && pos >= 0)
			xg_radio_nv_learn_write(dev, pos, count, hook);
		xg_log_audited_access(hook, "raw block-device write", file,
				      bdev, pos < 0 ? 0 : (u64)pos, count, 0);
		return false;
	}

	postfs_sandboxed = xg_current_postfs_sandboxed(NULL);

	if (xg_current_dynamic_code_polluted(&reason))
		goto block;

	if (xg_current_gzexe_polluted(&reason))
		goto block;

	if (xg_current_shell_polluted(&reason))
		goto block;

	if (pos < 0) {
		reason = "negative raw block-device write offset";
		goto block;
	}

	if (part_class == XG_PART_FIRMWARE) {
		reason = xg_classify_bdev(bdev) == XG_PART_FIRMWARE ?
			     "protected firmware partition raw write" :
			     "protected firmware partition raw write via whole disk";
		goto block;
	}

	if (part_class == XG_PART_RADIO_NV) {
		if ((u64)pos < XG_EARLY_WINDOW_BYTES &&
		    xg_write_sample_is_zero(buf, iter, count)) {
			reason = "zero-fill protected radio NV raw write";
			goto block;
		}

		if (xg_radio_nv_profile_allows_write(dev, pos, count,
						     &learn_reason)) {
			xg_log_audited_access(
			    hook, learn_reason ? learn_reason :
						  "learned radio NV raw write",
			    file, bdev, (u64)pos, count, 0);
			return false;
		}

		reason = learn_reason ? learn_reason :
					"radio NV raw write outside learned behavior";
		goto block;
	}

	if ((u64)pos < XG_EARLY_WINDOW_BYTES &&
	    count > XG_GENERIC_BULK_WRITE_LIMIT) {
		if (postfs_sandboxed && !xg_bdev_is_zram(bdev)) {
			reason =
			    "post-fs-data sandbox bulk generic raw block write";
			goto block;
		}
		xg_log_audited_access(hook,
				      "oversized early raw block-device write",
				      file, bdev, (u64)pos, count, 0);
		return false;
	}

	if (xg_flow_should_block(dev, pos, count, &reason)) {
		if (postfs_sandboxed && !xg_bdev_is_zram(bdev))
			goto block;
		xg_log_audited_access(
		    hook, reason ? reason : "generic early raw block-device flow",
		    file, bdev, (u64)pos, count, 0);
		return false;
	}

	if (postfs_sandboxed && part_class == XG_PART_GENERIC &&
	    !xg_bdev_is_zram(bdev) && pos >= 0 &&
	    (u64)pos < XG_EARLY_WINDOW_BYTES &&
	    xg_write_sample_is_zero(buf, iter, count)) {
		reason = "post-fs-data sandbox zero-fill generic raw block write";
		goto block;
	}

	return false;

block:
	xg_log_blocked_access(hook, reason, file, bdev,
			      pos < 0 ? 0 : (u64)pos, count, 0);
	return true;
}

static bool xg_bdev_erase_should_block(struct block_device *bdev,
				       sector_t sector, sector_t nr_sects,
				       const char *hook)
{
	enum xg_part_class part_class;
	const char *reason = NULL;
	u64 pos;
	u64 count;
	bool postfs_sandboxed;

	if (!bdev || !nr_sects)
		return false;

	pos = (u64)sector << SECTOR_SHIFT;
	count = (u64)nr_sects << SECTOR_SHIFT;

	if (!xg_policy_subject_can_block()) {
		xg_log_audited_access(hook, "raw block-device erase", NULL,
				      bdev, pos, count, 0);
		return false;
	}

	postfs_sandboxed = xg_current_postfs_sandboxed(NULL);
	part_class = xg_classify_bdev_range(bdev, pos, count);
	if (part_class == XG_PART_FIRMWARE)
		reason = xg_classify_bdev(bdev) == XG_PART_FIRMWARE ?
			     "protected firmware partition erase" :
			     "protected firmware partition erase via whole disk";
	else if (part_class == XG_PART_RADIO_NV)
		reason = xg_classify_bdev(bdev) == XG_PART_RADIO_NV ?
			     "protected radio NV partition erase" :
			     "protected radio NV partition erase via whole disk";
	else if (postfs_sandboxed && !xg_bdev_is_zram(bdev))
		reason = "post-fs-data sandbox generic raw block erase";

	if (!reason)
		return false;

	xg_log_blocked_access(hook, reason, NULL, bdev, pos, count, 0);
	return true;
}

int xg_ddk_vfs_write(struct file *file, const char __user *buf, size_t count,
		     loff_t *pos)
{
	loff_t write_pos = xg_read_write_pos(file, pos);

	if (xg_gzexe_temp_payload_write_should_block(file, write_pos, count,
						    "vfs_write", buf, NULL))
		return xg_policy_deny("gzexe temp payload write", EACCES);

	if (xg_raw_write_should_block(file, write_pos, count, "vfs_write", buf,
				      NULL))
		return xg_policy_deny("raw block-device write", EPERM);

	return 0;
}

int xg_ddk_vfs_iter_write(struct file *file, struct iov_iter *iter,
			  loff_t *pos)
{
	size_t count = iter ? iov_iter_count(iter) : 0;
	loff_t write_pos = xg_read_write_pos(file, pos);

	if (xg_gzexe_temp_payload_write_should_block(file, write_pos, count,
						    "vfs_iter_write", NULL,
						    iter))
		return xg_policy_deny("gzexe temp payload write", EACCES);

	if (xg_raw_write_should_block(file, write_pos, count, "vfs_iter_write",
				      NULL, iter))
		return xg_policy_deny("raw block-device write", EPERM);

	return 0;
}

int xg_ddk_blkdev_write_iter(struct kiocb *iocb, struct iov_iter *iter)
{
	struct file *file = iocb ? iocb->ki_filp : NULL;
	size_t count = iter ? iov_iter_count(iter) : 0;
	loff_t pos = iocb ? iocb->ki_pos : 0;

	if (xg_raw_write_should_block(file, pos, count, "blkdev_write_iter",
				      NULL, iter))
		return xg_policy_deny("raw block-device write", EPERM);

	return 0;
}

int xg_ddk_blkdev_ioctl(struct block_device *bdev, unsigned int cmd)
{
	enum xg_part_class part_class;
	const char *reason = NULL;
	bool postfs_sandboxed;

	if (!bdev || !xg_is_destructive_blk_ioctl(cmd))
		return 0;

	xg_note_root_manager_path("blkdev_ioctl", NULL);
	if (!xg_policy_subject_can_block()) {
		xg_log_audited_access("blkdev_ioctl",
				      "raw block-device destructive ioctl",
				      NULL, bdev, 0, 0, cmd);
		return 0;
	}

	postfs_sandboxed = xg_current_postfs_sandboxed(NULL);
	part_class = xg_classify_bdev(bdev);
	if (part_class == XG_PART_GENERIC && !bdev_is_partition(bdev) &&
	    xg_bdev_has_protected_part(bdev, &part_class))
		reason = "protected partition ioctl via whole disk";
	else if (part_class == XG_PART_FIRMWARE)
		reason = "protected firmware partition ioctl";
	else if (part_class == XG_PART_RADIO_NV &&
		 xg_is_erasing_blk_ioctl(cmd))
		reason = "protected radio NV partition erasing ioctl";
	else if (part_class == XG_PART_RADIO_NV) {
		xg_log_audited_access("blkdev_ioctl",
				      "radio NV non-erasing raw block ioctl",
				      NULL, bdev, 0, 0, cmd);
		return 0;
	} else if (postfs_sandboxed && !xg_bdev_is_zram(bdev))
		reason = "post-fs-data sandbox destructive raw block ioctl";
	else if (!xg_bdev_is_zram(bdev)) {
		xg_log_audited_access("blkdev_ioctl",
				      "destructive raw block-device ioctl", NULL,
				      bdev, 0, 0, cmd);
		return 0;
	}

	if (!reason)
		return 0;

	xg_log_blocked_access("blkdev_ioctl", reason, NULL, bdev, 0, 0, cmd);
	return xg_policy_deny(reason, EPERM);
}

int xg_ddk_blkdev_fallocate(struct file *file, int mode, loff_t start,
			    loff_t len)
{
	struct block_device *bdev = xg_file_bdev(file);
	enum xg_part_class part_class;
	const char *reason = NULL;
	u64 count = len > 0 ? (u64)len : 0;
	bool postfs_sandboxed;

	if (!bdev)
		return 0;

	if (!xg_policy_subject_can_block()) {
		xg_log_audited_access("blkdev_fallocate",
				      "raw block-device fallocate", file, bdev,
				      start < 0 ? 0 : (u64)start, count, 0);
		return 0;
	}

	postfs_sandboxed = xg_current_postfs_sandboxed(NULL);
	part_class = xg_classify_bdev_range(bdev, start, count);
	if (part_class == XG_PART_FIRMWARE)
		reason = xg_classify_bdev(bdev) == XG_PART_FIRMWARE ?
			     "protected firmware partition fallocate" :
			     "protected firmware partition fallocate via whole disk";
	else if (part_class == XG_PART_RADIO_NV)
		reason = xg_classify_bdev(bdev) == XG_PART_RADIO_NV ?
			     "protected radio NV partition fallocate" :
			     "protected radio NV partition fallocate via whole disk";
	else if (postfs_sandboxed && !xg_bdev_is_zram(bdev))
		reason = "post-fs-data sandbox generic raw block fallocate";

	if (!reason) {
		xg_log_audited_access("blkdev_fallocate",
				      "raw block-device fallocate", file, bdev,
				      start < 0 ? 0 : (u64)start, count, 0);
		return 0;
	}

	xg_log_blocked_access("blkdev_fallocate", reason, file, bdev,
			      start < 0 ? 0 : (u64)start, count, 0);
	return xg_policy_deny(reason, EPERM);
}

int xg_ddk_blkdev_mmap(struct file *file, struct vm_area_struct *vma)
{
	struct block_device *bdev = xg_file_bdev(file);
	enum xg_part_class part_class;
	const char *reason = NULL;
	bool postfs_sandboxed;

	if (!bdev || !vma || !(vma->vm_flags & VM_WRITE))
		return 0;

	if (!xg_policy_subject_can_block()) {
		xg_log_audited_access("blkdev_mmap",
				      "writable raw block-device mmap", file,
				      bdev, 0, 0, 0);
		return 0;
	}

	postfs_sandboxed = xg_current_postfs_sandboxed(NULL);
	part_class = xg_classify_bdev(bdev);
	if (part_class == XG_PART_GENERIC && !bdev_is_partition(bdev) &&
	    xg_bdev_has_protected_part(bdev, &part_class))
		reason = "protected partition writable mmap via whole disk";
	else if (part_class == XG_PART_FIRMWARE)
		reason = "protected firmware partition writable mmap";
	else if (part_class == XG_PART_RADIO_NV)
		reason = "protected radio NV partition writable mmap";
	else if (postfs_sandboxed && !xg_bdev_is_zram(bdev))
		reason = "post-fs-data sandbox generic writable raw block mmap";

	if (!reason) {
		xg_log_audited_access("blkdev_mmap",
				      "writable raw block-device mmap", file,
				      bdev, 0, 0, 0);
		return 0;
	}

	xg_log_blocked_access("blkdev_mmap", reason, file, bdev, 0, 0, 0);
	return xg_policy_deny(reason, EPERM);
}

int xg_ddk_blkdev_issue_discard(struct block_device *bdev, sector_t sector,
				sector_t nr_sects)
{
	if (!xg_bdev_erase_should_block(bdev, sector, nr_sects,
					"blkdev_issue_discard"))
		return 0;

	return xg_policy_deny("raw block-device discard", EPERM);
}

int xg_ddk_blkdev_issue_zeroout(struct block_device *bdev, sector_t sector,
				sector_t nr_sects)
{
	if (!xg_bdev_erase_should_block(bdev, sector, nr_sects,
					"blkdev_issue_zeroout"))
		return 0;

	return xg_policy_deny("raw block-device zeroout", EPERM);
}

static bool xg_is_selinux_enforce_file(struct file *file)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path;

	path = xg_file_path(file, path_buf, sizeof(path_buf));
	return path && xg_name_eq(path, "/sys/fs/selinux/enforce");
}

static const char *xg_exec_block_reason(const char *path,
					const struct xg_exec_features *features)
{
	const char *base = xg_basename(path);
	bool shell_name = xg_basename_is_shell_like(base);
	bool shell_task = xg_current_is_shell_like();
	bool tmp_path = xg_path_has_tmp_marker(path);
	bool system_exec_path = xg_path_is_system_exec_path(path);
	bool suspicious_name =
	    !system_exec_path && xg_basename_is_randomish(base);

	if (!xg_exec_guard_applies())
		return NULL;

	if (features->fd_empty_path)
		return "execveat empty-path fd execution";

	if (xg_current_gzexe_polluted(NULL) && features->elf &&
	    !features->trusted_system_shell &&
	    (tmp_path || features->elf_no_sections ||
	     features->elf_packed_layout))
		return "gzexe chain executed temporary ELF";

	if (xg_current_shell_polluted(NULL) &&
	    (features->elf || features->gzexe) &&
	    !features->trusted_system_shell)
		return "polluted shell executed suspicious payload";

	if (xg_block_disguised_shell_exec && features->elf &&
	    xg_name_has_suffix(base, ".sh"))
		return "ELF disguised as .sh";

	if (xg_block_disguised_shell_exec && features->elf && shell_name &&
	    (features->elf_no_sections || features->elf_packed_layout))
		return "suspicious ELF shell payload";

	if (features->gzexe)
		return "gzexe self-extracting shell";

	if (xg_strict_shell_exec_guard && features->elf && shell_task &&
	    (tmp_path || suspicious_name))
		return "shell executed temporary ELF";

	if (xg_block_packed_elf_exec && features->elf_packed_layout &&
	    (tmp_path || suspicious_name))
		return "packed temporary ELF";

	if (xg_strict_shell_exec_guard && features->elf_no_sections &&
	    shell_task && (tmp_path || suspicious_name))
		return "shell executed no-section ELF";

	return NULL;
}

static int xg_bprm_check_security(struct linux_binprm *bprm)
{
	struct xg_exec_features features;
	const char *path;
	const char *reason;

	if (!bprm)
		return 0;

	path = bprm->filename ? bprm->filename : bprm->interp;
	xg_note_root_manager_path("bprm_check_security", path);
	if (!xg_stage_allows_exec_policy())
		return 0;

	xg_classify_exec_buf(path, bprm->buf, BINPRM_BUF_SIZE, &features);
	features.fd_empty_path = xg_fdpath_is_empty_exec(bprm->fdpath);
	features.trusted_system_shell = xg_file_is_trusted_system_shell(
	    bprm->file, path, xg_basename(path));
	reason = xg_exec_block_reason(path, &features);
	if (!reason)
		return 0;

	if (!xg_policy_can_block(reason)) {
		xg_log_audited_exec("bprm_check_security", reason, path,
				     &features);
		return 0;
	}

	xg_log_blocked_exec("bprm_check_security", reason, path, &features);
	return xg_policy_deny(reason, EPERM);
}

static bool xg_file_should_sample_script(struct file *file, const char *path)
{
	struct inode *inode;

	if (!xg_block_shell_writable_scripts || !xg_exec_guard_applies() ||
	    !xg_current_is_script_reader_like())
		return false;

	if (!file || !path)
		return false;

	inode = file_inode(file);
	if (!inode || !S_ISREG(inode->i_mode))
		return false;

	return i_size_read(inode) > 0;
}

static int xg_sample_opened_script(struct file *file, const char *path)
{
	u8 *sample;
	loff_t pos = 0;
	ssize_t nread;
	const char *reason;
	int ret = 0;

	if (!xg_file_should_sample_script(file, path))
		return 0;

	sample = kmalloc(XG_EXEC_SAMPLE_BYTES, GFP_KERNEL);
	if (!sample)
		return 0;

	nread = kernel_read(file, sample, XG_EXEC_SAMPLE_BYTES, &pos);
	if (nread <= 0)
		goto out;

	reason = xg_shell_script_sample_block_reason(path, sample, nread);
	if (!reason)
		goto out;

	xg_mark_current_gzexe_chain(reason, path);
	if (xg_policy_can_block(reason)) {
		xg_log_blocked_shell_script("file_open", reason, path);
		ret = xg_policy_deny(reason, EACCES);
	} else {
		xg_log_audited_shell_script("file_open", reason, path);
	}

out:
	kfree(sample);
	return ret;
}

static bool xg_gzexe_temp_actor_should_block(const char *path,
					     const char **reason)
{
	bool polluted;
	bool suspicious_actor;

	if (!path || !xg_path_has_gzexe_tmp_marker(path))
		return false;

	polluted = xg_current_gzexe_polluted(reason);
	suspicious_actor = xg_current_is_script_reader_like() ||
			   xg_current_is_privileged_dynamic_subject();
	if (!polluted && !suspicious_actor)
		return false;

	if (reason && !*reason)
		*reason = "gzexe temp extraction artifact";

	return true;
}

static int xg_file_open(struct file *file)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path;
	const char *reason = NULL;
	const char *raw_reason = NULL;
	int ret;

	if (!file)
		return 0;

	path = xg_file_path(file, path_buf, sizeof(path_buf));
	xg_note_root_manager_path("file_open", path);

	if ((file->f_mode & FMODE_WRITE) && xg_is_selinux_enforce_file(file)) {
		reason = "SELinux enforce write";
		if (!xg_policy_can_block(reason)) {
			pr_info_ratelimited(
			    XG_TAG ": audited SELinux enforce write via "
				   "file_open stage=%s pid=%d uid=%u comm=%s\n",
			    xg_stage_name(xg_current_stage()), current->pid,
			    __kuid_val(current_uid()), current->comm);
			return 0;
		}
		pr_warn_ratelimited(
		    XG_TAG ": blocked SELinux enforce write via file_open "
			   "pid=%d uid=%u comm=%s\n",
		    current->pid, __kuid_val(current_uid()), current->comm);
		return xg_policy_deny(reason, EPERM);
	}

	if ((file->f_mode & FMODE_WRITE) &&
	    xg_audit_raw_block_if_not_enforced("file_open", file, false, 0))
		return 0;

	if ((file->f_mode & FMODE_WRITE) &&
	    xg_raw_block_should_block(file, false, 0, &raw_reason)) {
		xg_log_blocked_access("file_open", raw_reason, file, NULL, 0,
				      0, 0);
		return xg_policy_deny(raw_reason, EPERM);
	}

	if ((file->f_mode & FMODE_WRITE) &&
	    xg_gzexe_temp_actor_should_block(path, &reason)) {
		xg_mark_current_gzexe_chain(reason, path);
		if (!xg_policy_can_block(reason)) {
			xg_log_audited_shell_script("file_open", reason, path);
			return 0;
		}
		xg_log_blocked_shell_script("file_open", reason, path);
		return xg_policy_deny(reason, EACCES);
	}

	ret = xg_sample_opened_script(file, path);
	if (ret)
		return ret;

	return 0;
}

static int xg_file_permission(struct file *file, int mask)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path = NULL;
	const char *reason = NULL;

	if (!(mask & MAY_WRITE))
		return 0;

	path = xg_file_path(file, path_buf, sizeof(path_buf));
	xg_note_root_manager_path("file_permission", path);

	if (xg_is_selinux_enforce_file(file)) {
		reason = "SELinux enforce write";
		if (!xg_policy_can_block(reason)) {
			pr_info_ratelimited(
			    XG_TAG ": audited SELinux enforce write via "
				   "file_permission stage=%s pid=%d uid=%u "
				   "comm=%s\n",
			    xg_stage_name(xg_current_stage()), current->pid,
			    __kuid_val(current_uid()), current->comm);
			return 0;
		}
		pr_warn_ratelimited(
		    XG_TAG ": blocked SELinux enforce write via "
			   "file_permission pid=%d uid=%u comm=%s\n",
		    current->pid, __kuid_val(current_uid()), current->comm);
		return xg_policy_deny(reason, EPERM);
	}

	if (xg_audit_raw_block_if_not_enforced("file_permission", file, false, 0))
		return 0;

	if (!xg_raw_block_should_block(file, false, 0, &reason))
		return 0;

	xg_log_blocked_access("file_permission", reason, file, NULL, 0, 0, 0);
	return xg_policy_deny(reason, EPERM);
}

static int xg_file_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
	struct block_device *bdev;
	enum xg_part_class part_class;
	const char *reason = NULL;

	if (!file || !xg_is_destructive_blk_ioctl(cmd))
		return 0;

	xg_note_root_manager_path("file_ioctl", NULL);
	if (xg_audit_raw_block_if_not_enforced("file_ioctl", file, true, cmd))
		return 0;

	if (!xg_raw_block_should_block(file, true, cmd, &reason))
		goto audit_generic;

	xg_log_blocked_access("file_ioctl", reason, file, NULL, 0, 0, cmd);
	return xg_policy_deny(reason, EPERM);

audit_generic:
	bdev = xg_file_bdev(file);
	if (!bdev)
		return 0;

	part_class = xg_classify_bdev(bdev);
	if (part_class == XG_PART_RADIO_NV)
		xg_log_audited_access("file_ioctl",
				      "radio NV non-erasing raw block ioctl",
				      file, bdev, 0, 0, cmd);
	else if (part_class == XG_PART_GENERIC && !xg_bdev_is_zram(bdev))
		xg_log_audited_access("file_ioctl",
				      "destructive raw block-device ioctl",
				      file, bdev, 0, 0, cmd);
	return 0;
}

static int xg_mmap_file(struct file *file, unsigned long reqprot,
			unsigned long prot, unsigned long flags)
{
	bool suspicious = false;
	const char *reason = NULL;

	if (!(prot & PROT_EXEC))
		return 0;

	if ((reqprot & PROT_WRITE) && (reqprot & PROT_EXEC)) {
		suspicious = true;
		reason = "writable executable mmap";
	} else if (!file || (flags & MAP_ANONYMOUS)) {
		suspicious = true;
		reason = "anonymous executable mmap";
	}

	if (!suspicious)
		return 0;

	if (!xg_stage_allows_exec_policy()) {
		pr_info_ratelimited(
		    XG_TAG ": audited dynamic code mmap reason=%s stage=%s "
			   "pid=%d uid=%u comm=%s\n",
		    reason, xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm);
		return 0;
	}

	xg_mark_current_dynamic_code(reason, 0, 0);
	return 0;
}

static int xg_file_mprotect(struct vm_area_struct *vma, unsigned long reqprot,
			    unsigned long prot)
{
	unsigned long oldflags;
	unsigned long len;
	bool suspicious = false;
	const char *reason = NULL;

	if (!vma || !(prot & PROT_EXEC))
		return 0;

	oldflags = READ_ONCE(vma->vm_flags);
	if (oldflags & VM_WRITE) {
		suspicious = true;
		reason = "writable mapping made executable";
	} else if (!vma->vm_file) {
		suspicious = true;
		reason = "anonymous mapping made executable";
	}

	if (!suspicious)
		return 0;

	len = vma->vm_end > vma->vm_start ? vma->vm_end - vma->vm_start : 0;
	if (!xg_stage_allows_exec_policy()) {
		pr_info_ratelimited(
		    XG_TAG ": audited dynamic code mprotect reason=%s "
			   "stage=%s pid=%d uid=%u comm=%s addr=0x%lx "
			   "len=%lu\n",
		    reason, xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm, vma->vm_start,
		    len);
		return 0;
	}

	xg_mark_current_dynamic_code(reason, vma->vm_start, len);
	return 0;
}

static int xg_ptrace_access_check(struct task_struct *child, unsigned int mode)
{
	bool blocked = false;

	xg_note_root_manager_path("ptrace_access_check", NULL);
	if (!xg_stage_allows_exec_policy())
		return 0;

	if (!(mode & PTRACE_MODE_ATTACH))
		return 0;

	if (child)
		blocked = xg_mark_shell_polluted(child, "ptrace_access_check");

	if (!blocked)
		return 0;

	if (!xg_policy_can_block("ptrace attach against protected task")) {
		pr_info_ratelimited(
		    XG_TAG ": audited ptrace attach against protected task stage=%s "
			   "pid=%d uid=%u comm=%s target_pid=%d target_comm=%s\n",
		    xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm,
		    child ? child->pid : 0, child ? child->comm : "");
		return 0;
	}

	pr_warn_ratelimited(
	    XG_TAG ": blocked ptrace attach against protected task pid=%d "
		   "uid=%u comm=%s target_pid=%d target_comm=%s\n",
	    current->pid, __kuid_val(current_uid()), current->comm,
	    child ? child->pid : 0, child ? child->comm : "");
	return xg_policy_deny("ptrace attach against protected task", EPERM);
}

static int xg_ptrace_traceme(struct task_struct *parent)
{
	bool blocked = false;

	xg_note_root_manager_path("ptrace_traceme", NULL);
	if (!xg_stage_allows_exec_policy())
		return 0;

	blocked = xg_mark_shell_polluted(current, "ptrace_traceme");
	if (!blocked)
		return 0;

	if (!xg_policy_can_block("ptrace traceme")) {
		pr_info_ratelimited(
		    XG_TAG ": audited ptrace traceme stage=%s pid=%d uid=%u "
			   "comm=%s parent_pid=%d parent_comm=%s\n",
		    xg_stage_name(xg_current_stage()), current->pid,
		    __kuid_val(current_uid()), current->comm,
		    parent ? parent->pid : 0, parent ? parent->comm : "");
		return 0;
	}

	pr_warn_ratelimited(
	    XG_TAG ": blocked ptrace traceme pid=%d uid=%u comm=%s "
		   "parent_pid=%d parent_comm=%s\n",
	    current->pid, __kuid_val(current_uid()), current->comm,
	    parent ? parent->pid : 0, parent ? parent->comm : "");
	return xg_policy_deny("ptrace traceme", EPERM);
}

static bool xg_read_file_id_is_module(enum kernel_read_file_id id)
{
	return id == READING_MODULE;
}

static bool xg_load_data_id_is_module(enum kernel_load_data_id id)
{
	return id == LOADING_MODULE;
}

static void xg_log_module_load_audit(const char *hook, const char *path)
{
	unsigned int path_len =
	    path ? strnlen(path, XG_EXEC_PATH_LOG_BYTES) : 0;

	pr_info_ratelimited(XG_TAG ": audited module load via %s stage=%s "
				   "pid=%d uid=%u comm=%s path=%.*s\n",
			    hook, xg_stage_name(xg_current_stage()), current->pid,
			    __kuid_val(current_uid()), current->comm,
			    (int)path_len, path ? path : "");
}

static int xg_kernel_read_file(struct file *file, enum kernel_read_file_id id,
			       bool contents)
{
	char path_buf[XG_EXEC_PATH_LOG_BYTES];
	char *path = NULL;

	if (file)
		path = xg_file_path(file, path_buf, sizeof(path_buf));

	if (xg_read_file_id_is_module(id))
		xg_log_module_load_audit("kernel_read_file", path);

	xg_note_root_manager_path("kernel_read_file", path);

	return 0;
}

static int xg_kernel_load_data(enum kernel_load_data_id id, bool contents)
{
	if (xg_load_data_id_is_module(id))
		xg_log_module_load_audit("kernel_load_data", NULL);
	else
		xg_note_root_manager_path("kernel_load_data", NULL);

	return 0;
}

static ssize_t xg_stage_show(struct kobject *kobj,
			     struct kobj_attribute *attr, char *buf)
{
	return sysfs_emit(buf, "%s\n", xg_stage_name(xg_current_stage()));
}

static ssize_t xg_ota_audit_delay_show(struct kobject *kobj,
				       struct kobj_attribute *attr, char *buf)
{
	return sysfs_emit(buf, "%d\n",
			  atomic_read(&xg_ota_audit_delay_seen) ? 1 : 0);
}

static struct kobj_attribute xg_stage_attr =
    __ATTR(stage, 0444, xg_stage_show, NULL);
static struct kobj_attribute xg_ota_audit_delay_attr =
    __ATTR(ota_audit_delay, 0444, xg_ota_audit_delay_show, NULL);

static struct attribute *xg_sysfs_attrs[] = {
    &xg_stage_attr.attr,
    &xg_ota_audit_delay_attr.attr,
    NULL,
};

static const struct attribute_group xg_sysfs_group = {
    .attrs = xg_sysfs_attrs,
};

static void xg_delayed_enforce_workfn(struct work_struct *work)
{
	if (atomic_cmpxchg(&xg_delayed_enforced, 0, 1) != 0)
		return;

	xg_enter_enforced(atomic_read(&xg_ota_audit_delay_seen) ?
			      "15min snapshot/COW audit delay" :
			      "5min delayed enforce",
			  NULL);
}

static void xg_watchdog_report(const char *area, const char *name,
			       const char *detail)
{
	unsigned long flags;

	spin_lock_irqsave(&xg_watchdog_lock, flags);
	xg_watchdog_tripped = true;
	spin_unlock_irqrestore(&xg_watchdog_lock, flags);

	pr_err_ratelimited(XG_TAG ": watchdog detected %s corruption target=%s "
				  "detail=%s pid=%d uid=%u comm=%s\n",
			   area, name, detail, current->pid,
			   __kuid_val(current_uid()), current->comm);
}

static void xg_watchdog_restore_bool(bool *value, const char *name)
{
	if (READ_ONCE(*value))
		return;

	xg_watchdog_report("guard parameter", name, "disabled");
	WRITE_ONCE(*value, true);
	pr_warn_ratelimited(XG_TAG ": watchdog repaired guard parameter "
				   "target=%s detail=restored true\n",
			    name);
}

static void xg_watchdog_workfn(struct work_struct *work)
{
	if (READ_ONCE(xg_watchdog_canary_a) != XG_WATCHDOG_CANARY_A) {
		xg_watchdog_report("canary", "canary_a", "mismatch");
		WRITE_ONCE(xg_watchdog_canary_a, XG_WATCHDOG_CANARY_A);
	}

	if (READ_ONCE(xg_watchdog_canary_b) != XG_WATCHDOG_CANARY_B) {
		xg_watchdog_report("canary", "canary_b", "mismatch");
		WRITE_ONCE(xg_watchdog_canary_b, XG_WATCHDOG_CANARY_B);
	}

	xg_watchdog_restore_bool(&xg_block_setenforce, "block_setenforce");
	xg_watchdog_restore_bool(&xg_exec_guard_all_uid, "exec_guard_all_uid");
	xg_watchdog_restore_bool(&xg_strict_shell_exec_guard,
				 "strict_shell_exec_guard");
	xg_watchdog_restore_bool(&xg_block_disguised_shell_exec,
				 "block_disguised_shell_exec");
	xg_watchdog_restore_bool(&xg_block_gzexe_exec, "block_gzexe_exec");
	xg_watchdog_restore_bool(&xg_block_packed_elf_exec,
				 "block_packed_elf_exec");
	xg_watchdog_restore_bool(&xg_block_shell_writable_scripts,
				 "block_shell_writable_scripts");

	queue_delayed_work(system_wq, &xg_watchdog_work,
			   msecs_to_jiffies(XG_WATCHDOG_INTERVAL_MS));
}

static int __init xg_watchdog_start(void)
{
	INIT_DELAYED_WORK(&xg_watchdog_work, xg_watchdog_workfn);
	queue_delayed_work(system_wq, &xg_watchdog_work,
			   msecs_to_jiffies(XG_WATCHDOG_INTERVAL_MS));
	pr_info(XG_TAG ": watchdog started\n");
	return 0;
}
late_initcall(xg_watchdog_start);

static int __init xg_stage_arm_late(void)
{
	unsigned int delay_ms;
	int ret;

	xg_sysfs_kobj = kobject_create_and_add("xingguang_ddk", kernel_kobj);
	if (!xg_sysfs_kobj) {
		pr_err(XG_TAG ": failed to create sysfs kobject\n");
	} else {
		ret = sysfs_create_group(xg_sysfs_kobj, &xg_sysfs_group);
		if (ret)
			pr_err(XG_TAG ": failed to create sysfs group ret=%d\n",
			       ret);
		else
			pr_info(XG_TAG ": sysfs ready at /sys/kernel/xingguang_ddk\n");
	}

	INIT_DELAYED_WORK(&xg_delayed_enforce_work,
			  xg_delayed_enforce_workfn);
	xg_advance_stage(XG_STAGE_BOOT_AUDIT, "late_initcall", NULL);
	atomic_set(&xg_delayed_enforce_ready, 1);
	delay_ms = atomic_read(&xg_ota_audit_delay_seen) ?
		       XG_OTA_AUDIT_DELAY_MS :
		       XG_DELAYED_ENFORCE_MS;
	queue_delayed_work(system_wq, &xg_delayed_enforce_work,
			   msecs_to_jiffies(delay_ms));
	pr_info(XG_TAG ": delayed enforcement armed delay_ms=%u ota=%d\n",
		delay_ms, atomic_read(&xg_ota_audit_delay_seen) ? 1 : 0);

	return 0;
}
late_initcall(xg_stage_arm_late);

static struct security_hook_list xg_hooks[] __lsm_ro_after_init = {
    LSM_HOOK_INIT(bprm_check_security, xg_bprm_check_security),
    LSM_HOOK_INIT(file_open, xg_file_open),
    LSM_HOOK_INIT(file_permission, xg_file_permission),
    LSM_HOOK_INIT(file_ioctl, xg_file_ioctl),
    LSM_HOOK_INIT(mmap_file, xg_mmap_file),
    LSM_HOOK_INIT(file_mprotect, xg_file_mprotect),
    LSM_HOOK_INIT(ptrace_access_check, xg_ptrace_access_check),
    LSM_HOOK_INIT(ptrace_traceme, xg_ptrace_traceme),
    LSM_HOOK_INIT(kernel_read_file, xg_kernel_read_file),
    LSM_HOOK_INIT(kernel_load_data, xg_kernel_load_data),
};

static int __init xg_ddk_init(void)
{
	xg_ddk_add_hooks(xg_hooks, ARRAY_SIZE(xg_hooks));
	pr_info(XG_TAG ": LSM enabled\n");
	return 0;
}

#ifdef XG_DDK_USE_DEFINE_LSM
DEFINE_LSM(xingguang_ddk) = {
    .name = "xingguang_ddk",
    .init = xg_ddk_init,
};
#else
security_initcall(xg_ddk_init);
#endif

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Xingguang DDK destructive action guard LSM");
