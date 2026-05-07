#ifndef XINGGUANG_DDK_KERNEL_COMPAT_H
#define XINGGUANG_DDK_KERNEL_COMPAT_H

#include <linux/lsm_hooks.h>
#include <linux/security.h>
#include <linux/version.h>

#ifdef DEFINE_LSM
#define XG_DDK_USE_DEFINE_LSM 1
#endif

#ifndef __ro_after_init
#define __ro_after_init
#endif

#ifndef __lsm_ro_after_init
#define __lsm_ro_after_init __ro_after_init
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 8, 0)
static const struct lsm_id xg_ddk_lsmid = {
    .name = "xingguang_ddk",
    .id = 996,
};
#endif

static inline void __init xg_ddk_add_hooks(struct security_hook_list *hooks,
					   int count)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 8, 0)
	security_add_hooks(hooks, count, &xg_ddk_lsmid);
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
	security_add_hooks(hooks, count, "xingguang_ddk");
#else
	security_add_hooks(hooks, count);
#endif
}

#endif
